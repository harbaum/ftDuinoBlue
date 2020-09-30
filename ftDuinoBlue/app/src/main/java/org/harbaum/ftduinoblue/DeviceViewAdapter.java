package org.harbaum.ftduinoblue;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DeviceViewAdapter extends RecyclerView.Adapter<DeviceViewAdapter.ViewHolder> {
    private ArrayList<DeviceViewAdapter.DeviceEntry> mData;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;
    private int mBusyPosition = -1;

    public static class DeviceEntry implements Parcelable {
        String mName, mAddress;
        DeviceEntry(String name, String address) {
            mName = name;
            mAddress = address;
        }

        public String getName() {
            return mName;
        }
        public String getAddress() {
            return mAddress;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(mName);
            parcel.writeString(mAddress);
        }

        public DeviceEntry (Parcel parcel) {
            mName = parcel.readString();
            mAddress = parcel.readString();
        }

        // Method to recreate a Question from a Parcel
        public static Creator<DeviceEntry> CREATOR = new Creator<DeviceEntry>() {

            @Override
            public DeviceEntry createFromParcel(Parcel source) {
                return new DeviceEntry(source);
            }

            @Override
            public DeviceEntry[] newArray(int size) {
                return new DeviceEntry[size];
            }
        };
    }

    // data is passed into the constructor
    public
    DeviceViewAdapter(Context context, ArrayList <DeviceEntry> savedDevices) {
        this.mInflater = LayoutInflater.from(context);
        if(savedDevices == null)
            this.mData = new ArrayList<>();
        else
            this.mData = savedDevices;
    }

    public boolean isBusy() {
        return(mBusyPosition >= 0);
    }

    public void setBusy(int index) {
        if(mBusyPosition >= 0)
            notifyItemChanged(mBusyPosition);
        if(index >= 0)
            notifyItemChanged(index);

        mBusyPosition = index;
    }

    public void append(String name, String addr) {

        // check if we have that device already in our list
        for(int i=0;i<this.mData.size();i++) {
            // check if addresses are equal
            if (this.mData.get(i).getAddress().equals(addr)) {
                // check if the device got a name and we don't already have one
                if((this.mData.get(i).getName() == null || this.mData.get(i).getName().isEmpty()) &&
                   (name != null && !name.isEmpty())) {
                    Log.d("Main", "Device got a missing name! Updating it");
                    this.mData.set(i, new DeviceEntry(name, addr));
                    notifyItemChanged(i);
                }
                return;
            }
        }

        // device not known yet ...
        mData.add(new DeviceEntry(name, addr));
        notifyItemInserted(mData.size()-1);
    }

    // inflates the row layout from xml when needed
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.device_view, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        DeviceEntry res = mData.get(position);

        // make only the progressbar in the busy entry visible
        holder.progressBar.setVisibility(position == mBusyPosition?View.VISIBLE:View.INVISIBLE);

        if(res.getName() != null && !res.getName().isEmpty())
            holder.nameView.setText(res.getName());
        else
            holder.nameView.setText(R.string.no_name);

        holder.addrView.setText(res.getAddress());
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size();
    }

    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView nameView, addrView;
        ProgressBar progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.device_name);
            addrView = itemView.findViewById(R.id.device_addr);
            progressBar = itemView.findViewById(R.id.progressBar);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }
    }

    // convenience method for getting data at click position
    String getDevice(int id) {
        return mData.get(id).getAddress();
    }

    // allows clicks events to be caught
    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public ArrayList<? extends Parcelable> getDeviceList() {
        return mData;
    }

    public void clearList() {
        mData = new ArrayList<>();
        notifyDataSetChanged();
    }

    // parent activity will implement this method to respond to click events
    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }
}