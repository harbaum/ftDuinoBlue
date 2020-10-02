package org.harbaum.ftduinoblue;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Handler;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.slider.Slider;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.controlwear.virtual.joystick.android.JoystickView;
import prettify.PrettifyParser;
import syntaxhighlight.ParseResult;
import syntaxhighlight.Parser;

import static java.lang.Math.abs;

public class ControlActivity extends AppCompatActivity {
    private final static String TAG = ControlActivity.class.getSimpleName();

    private RelativeLayout mRelativeLayout;
    private LayoutXmlParser.Layout mLayout = null;
    private String mXml = null;
    private boolean mInitiatedDisconnection = false;
    private List<View> mViews = new ArrayList<>();
    private boolean mIsDemo = false;

    private SensorManager mSensorManager = null;
    private float mSensorAccel[] = new float[3];
    private float mSensorMag[] = new float[3];
    private Handler mHandler = new Handler();
    private Runnable mSensorLimiter = null;
    private List<LayoutXmlParser.Item> mSensors = new ArrayList<>();

    void parseMessage(String cmd, String data) {
        // split data string into seperate parts
        String[] dataParts = data.trim().split(" +", 2);
        // we need at least the id field
        if (dataParts.length >= 1) {
            try {
                int id = Integer.parseInt(dataParts[0]);

                // try to find an item with this id
                for (View view : mViews) {
                    if (view.getId() == id) {
                        // use this to update views
                        switch (cmd.trim().toLowerCase()) {
                            case "text":
                                // this needs another parameter
                                if(dataParts.length == 2) {
                                    // the text can be set on switches and labels only
                                    String text = dataParts[1].trim();
                                    if(view instanceof TextView) ((TextView)view).setText(text);
                                    if(view instanceof Switch)   ((Switch)view).setText(text);
                                    if(view instanceof Button)   ((Button)view).setText(text);
                                }
                                break;

                            case "switch":
                                // this needs another parameter
                                if(dataParts.length == 2) {
                                    // only switches have a switch state
                                    boolean on = dataParts[1].trim().equalsIgnoreCase("ON");
                                    if(view instanceof SwitchCompat)
                                        ((SwitchCompat)view).setChecked(on);
                                }
                                break;

                            case "slider":
                                // this needs another parameter
                                if(dataParts.length == 2) {
                                    // only switches have a switch state
                                    int value = Integer.parseInt(dataParts[1].trim());
                                    if(view instanceof SeekBar)
                                        ((SeekBar)view).setProgress(value);
                                }
                                break;

                            case "color":
                                // color needs another parameter
                                if(dataParts.length == 2) {
                                    Integer c = null;
                                    try {
                                        String color = dataParts[1].trim();
                                        c = Color.parseColor(color);
                                    } catch(IllegalArgumentException e) { /*ignore */ }

                                    if(c != null) {
                                        if (view instanceof TextView) ((TextView) view).setTextColor(c);
                                        if (view instanceof Switch)   ((Switch) view).setTextColor(c);
                                        if (view instanceof Button)   ((Button) view).setTextColor(c);
                                        if (view instanceof SeekBar)  ((SeekBar) view).getThumb().setTint(c);
                                    }
                                }
                                break;

                            case "bgcolor":
                                // bgcolor needs another parameter
                                if(dataParts.length == 2) {
                                    Integer c = null;
                                    try {
                                        String color = dataParts[1].trim();
                                        c = Color.parseColor(color);
                                    } catch(IllegalArgumentException e) { /*ignore */ }

                                    if(c != null) {
                                        if (view instanceof TextView) ((TextView) view).setBackgroundColor(c);
                                        if (view instanceof Switch)   ((Switch) view).setBackgroundColor(c);
                                        if (view instanceof Button)   ((Button) view).setBackgroundColor(c);
                                        if (view instanceof SeekBar)  ((SeekBar) view).setBackgroundColor(c);
                                    }
                                }
                                break;

                            default:
                                Log.w(TAG, "unexpected command: "+ cmd);
                                break;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // don't do anything ...
            }
        }
    }

    private final BroadcastReceiver mHm10ServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                // Hm10 service reports that the connection to the target device has been lost
                case Hm10Service.ACTION_DISCONNECTED:
                    // take action if we haven't triggerend the disconnection ourselves
                    if (!mInitiatedDisconnection) {
                        Toast.makeText(ControlActivity.this, getString(R.string.con_lost), Toast.LENGTH_LONG).show();
                        Log.w(TAG, "Lost connection to client!");
                        finish();
                    }
                    break;

                // Hm10 service reports that a message has been received
                case Hm10Service.ACTION_NOTIFY_MESSAGE:
                    String cmd = intent.getStringExtra("cmd");
                    String data = intent.getStringExtra("data");
                    if (cmd != null && data != null)
                        parseMessage(cmd, data);
                    break;
            }
        }
    };

    void sendRequest(String s) {
        Log.d(TAG, "sendRequest(" + s + ")");
        Intent intent = new Intent();
        intent.setAction(s);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendMessage(String msg) {
        Intent i = new Intent();
        i.setAction(Hm10Service.ACTION_SEND_MESSAGE);
        i.putExtra("message", msg);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    // this is called whenever the Activity is destroyed. This even happens if the user
    // rotates the device ...
    @Override
    protected void onDestroy() {
        Log.w(TAG, "destroy");
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mHm10ServiceReceiver);

        if(mSensorManager != null)
            mSensorManager.unregisterListener(mEventListener);
    }

    View addSpace(final LayoutXmlParser.Space s) {
        Space space = new Space(this);
        space.setId(s.id());
        space.setLayoutParams(s.layoutParams());
        mRelativeLayout.addView(space);
        return space;
    }

    @SuppressLint("ClickableViewAccessibility")
    View addButton(final LayoutXmlParser.Button b) {
        Button button = new Button(this);
        button.setId(b.id());
        button.setEnabled(b.enabled());
        if (b.size() != null) button.setTextSize(TypedValue.COMPLEX_UNIT_PX, b.size());
        button.setText(b.text());
        button.setLayoutParams(b.layoutParams());

        if (b.color() != null) button.setTextColor(b.color());
        if (b.bgcolor() != null)
            button.getBackground().setColorFilter(b.bgcolor(), PorterDuff.Mode.MULTIPLY);

        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // PRESSED
                        sendMessage("BUTTON " + b.id() + " DOWN");
                        return true; // if you want to handle the touch event
                    case MotionEvent.ACTION_UP:
                        // RELEASED
                        sendMessage("BUTTON " + b.id() + " UP");
                        return true; // if you want to handle the touch event
                }
                return false;
            }
        });
        mRelativeLayout.addView(button);
        return button;
    }

    View addSwitch(final LayoutXmlParser.Switch s) {
        SwitchCompat sw = new SwitchCompat(this);
        sw.setId(s.id());
        sw.setEnabled(s.enabled());
        if (s.size() != null) sw.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.size());
        sw.setText(s.text());
        sw.setLayoutParams(s.layoutParams());

        if (s.color() != null) sw.setTextColor(s.color());
        if (s.bgcolor() != null)
            sw.getBackground().setColorFilter(s.bgcolor(), PorterDuff.Mode.MULTIPLY);

        // send any switch changes as a message to be send via Hm10
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sendMessage("SWITCH " + s.id() + " " + (isChecked ? "ON" : "OFF"));
            }
        });

        mRelativeLayout.addView(sw);
        return sw;
    }

    View addLabel(final LayoutXmlParser.Label l) {
        TextView label = new TextView(this);     // create a new Button
        label.setId(l.id());
        label.setText(Html.fromHtml(l.text()));
        label.setLayoutParams(l.layoutParams());

        if (l.size() != null) label.setTextSize(TypedValue.COMPLEX_UNIT_PX, l.size());
        if (l.color() != null) label.setTextColor(l.color());

        mRelativeLayout.addView(label);
        return label;
    }

    // http://squircular.blogspot.com/2015/09/mapping-circle-to-square.html
    private double[] ellipticalDiscToSquare(double u, double v)
    {
        double u2 = u * u;
        double v2 = v * v;
        double twosqrt2 = 2.0 * Math.sqrt(2.0);
        double subtermx = 2.0 + u2 - v2;
        double subtermy = 2.0 - u2 + v2;
        double termx1 = subtermx + u * twosqrt2;
        double termx2 = subtermx - u * twosqrt2;
        double termy1 = subtermy + v * twosqrt2;
        double termy2 = subtermy - v * twosqrt2;

        double[] result = new double[2];
        result[0] = 0.5 * Math.sqrt(termx1) - 0.5 * Math.sqrt(termx2);
        result[1] = 0.5 * Math.sqrt(termy1) - 0.5 * Math.sqrt(termy2);

        return result;
    }

    View addJoystick(final LayoutXmlParser.Joystick j) {
        JoystickView joystick = new JoystickView(this);
        joystick.setId(j.id());
        joystick.setEnabled(j.enabled());

        // if a size has been given it overwrites width and height
        if(j.size() != null) {
            j.layoutParams().height = j.size();
            j.layoutParams().width = j.size();
        }

        joystick.setLayoutParams(j.layoutParams());
        if (j.color() != null) joystick.setButtonColor(j.color());
        if (j.bgcolor() != null) joystick.setBackgroundColor(j.bgcolor());

        // send movements at a max rate of 5 Hz (200ms)
        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                // mode 1: angle+strength
                int x = angle;
                int y = strength;

                // x/y mode
                if(j.mode() == 0) {
                    // convert polar coordinates into x/y
                    x = (int) (strength * Math.cos(angle * Math.PI / 180));
                    y = (int) (strength * Math.sin(angle * Math.PI / 180));
                } else if(j.mode() == 2) {
                    // convert polar coordinates into x/y
                    double u = (strength * Math.cos(angle * Math.PI / 180))/100;
                    double v = (strength * Math.sin(angle * Math.PI / 180))/100;
                    // map circle coordinates onto square
                    double[] result = ellipticalDiscToSquare(u,v);

                    x = (int)(result[0] * 100);
                    y = (int)(result[1] * 100);
                }
                sendMessage("JOYSTICK " + j.id() + " " + x + " " + y);
            }
        }, 200);

        mRelativeLayout.addView(joystick);
        return joystick;
    }

    public class VerticalSeekBar extends SeekBar {
        public VerticalSeekBar(Context context) {
            super(context);
        }

        public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public VerticalSeekBar(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(h, w, oldh, oldw);
        }

       @Override
        public synchronized void setProgress(int progress)  // it is necessary for calling setProgress on click of a button
       {
           super.setProgress(progress);
           onSizeChanged(getWidth(), getHeight(), 0, 0);
       }

        @Override
        protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(heightMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
        }

        protected void onDraw(Canvas c) {
            c.rotate(-90);
            c.translate(-getHeight(), 0);

            super.onDraw(c);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) {
                return false;
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    setProgress(getMax() - (int) (getMax() * event.getY() / getHeight()));
                    onSizeChanged(getWidth(), getHeight(), 0, 0);
                    break;

                case MotionEvent.ACTION_CANCEL:
                    break;
            }
            return true;
        }
    }

    View addSlider(final LayoutXmlParser.Slider s) {
        SeekBar slider = (!s.vertical())?new SeekBar(this):new VerticalSeekBar(this);

        slider.setId(s.id());
        slider.setEnabled(s.enabled());
        slider.setLayoutParams(s.layoutParams());

        // add to list of sensor using elements if needed
        if(s.sensor() != 0) mSensors.add(s);

        if(s.max() != null) slider.setMax(s.max());
        if (s.color() != null) slider.getThumb().setTint(s.color());
        if (s.bgcolor() != null) slider.setBackgroundColor(s.bgcolor());

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int value = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendMessage("SLIDER " + s.id() + " " + value);
            }
        });

        mRelativeLayout.addView(slider);
        return slider;
    }

    // The connection to the device is supposed to be closed when the user
    // goes back to the main scanner view.
    @Override
    public void onBackPressed() {
        Log.d(TAG, "android back button");

        // request disconnection
        mInitiatedDisconnection = true;
        sendRequest(Hm10Service.ACTION_DISCONNECT);

        super.onBackPressed();
    }

    public static class PrettifyHighlighter {
        private Parser mParser;
        private int mIndent;
        private Context mContext;
        private String mLastType;
        private boolean mFirst;

        public
        PrettifyHighlighter(Context context) {
            mParser = new PrettifyParser();
            mLastType = null;
            mContext = context;
            mIndent = 0;
            mFirst = true;
        }

        private String escapeHTML(String s) {
            StringBuilder out = new StringBuilder(Math.max(16, s.length()));
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c > 127 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '&') {
                    out.append("&#");
                    out.append((int) c);
                    out.append(';');
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }

        private
        String indentStr(int s) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < s; i++) stringBuilder.append("&nbsp;&nbsp;&nbsp;&nbsp;");
            return stringBuilder.toString();
        }

        public String highlight(String fileExtension, String sourceCode) {
            StringBuilder highlighted = new StringBuilder();
            List<ParseResult> results = mParser.parse(fileExtension, sourceCode);
            for (ParseResult result : results) {
                String type = result.getStyleKeys().get(0);
                String content = sourceCode.substring(result.getOffset(), result.getOffset() + result.getLength());
                highlighted.append(hl(type, content));
            }
            return highlighted.toString();
        }

        private String hl(String type, String content) {
            // remove all whitespace only content
            if(type.equals("pln") && content.trim().length() == 0)
                return "";

            final String FONT_PATTERN = "<font color=\"%s\">%s</font>";

            // get matching color
            int colorIdx = -1;
            if(type.equals("tag")) colorIdx = R.color.xmlTag;       // tag
            else if(type.equals("atn")) colorIdx = R.color.xmlAtn;  // attribute name
            else if(type.equals("atv")) colorIdx = R.color.xmlAtv;  // attribute value
            else if(type.equals("com")) colorIdx = R.color.xmlCom;  // comment
            else if(type.equals("pln")) colorIdx = R.color.xmlPln;  // plain text
            else if(type.equals("pun")) colorIdx = R.color.xmlPun;  // punctuation
            else Log.w(TAG, "Unhandled:"+type);

            // convert color id into color
            String color = (colorIdx<0)?"red":String.format("#%06X", (0xFFFFFF & mContext.getResources().getColor(colorIdx)));

            StringBuilder stringBuilder = new StringBuilder();

            if(type.equals("tag")) {
                stringBuilder.append("<b>");
                if (content.startsWith("</")) mIndent--;
                else if (content.endsWith(">") && !content.endsWith("/>")) mIndent++;

                if (content.startsWith("<")) {
                    if(!mFirst) stringBuilder.append("<br>");
                    stringBuilder.append(indentStr(mIndent));
                }
            } else if(type.equals("atn")) {
                stringBuilder.append(" ");
            } else if(mLastType != null && mLastType.equals("tag")) {
                // if last was a tag and this one is not, then indent anyway
                stringBuilder.append("<br>");
                stringBuilder.append(indentStr(mIndent));
            }

            mLastType = type;

            stringBuilder.append(String.format(FONT_PATTERN, color, escapeHTML(content)));
            if(type.equals("tag")) stringBuilder.append("</b>");

            mFirst = false;
            return stringBuilder.toString();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if(item.getItemId() == R.id.show_xml) {
            //respond to menu item selection
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            final View customLayout = getLayoutInflater().inflate(R.layout.xml_view, null);
            builder.setView(customLayout);

            AlertDialog alertDialog = builder.create();
            alertDialog.setTitle(getString(R.string.xml_title));

            PrettifyHighlighter highlighter = new PrettifyHighlighter(this);
            String highlighted = highlighter.highlight("xml", mXml);

            TextView tv = customLayout.findViewById(R.id.xml_text_view);
            tv.setMovementMethod(new ScrollingMovementMethod());
            tv.setHorizontallyScrolling(true);
            tv.setText(Html.fromHtml(highlighted));

            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.ok),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alertDialog.show();
            return true;
        }

        if(item.getItemId() == android.R.id.home) {
            Log.d(TAG, "user actionbar back");

            // request disconnection
            mInitiatedDisconnection = true;
            sendRequest(Hm10Service.ACTION_DISCONNECT);

            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private LayoutXmlParser.Layout parse(String xml) {
        LayoutXmlParser parser = new LayoutXmlParser();

        // set scale so parser knowns how to scale objects for screen layout
        parser.setScale(ControlActivity.this.getResources().getDisplayMetrics().density);

        try {
            return parser.parse(new ByteArrayInputStream(xml.getBytes()));
        } catch (XmlPullParserException | IOException e) {
            // this should actually never happen as this has already
            // been parsed in a dry-run ny the Hm10Server
            Log.e(TAG, "PARSE EXCEPTION! " + e.toString());
            return null;
        }
    }

    private void assembleLayout() {
        // setup the main layout
        setTitle(mLayout != null ? mLayout.name() : "Control");
        setRequestedOrientation(mLayout.orientation());

        if (mLayout.bgcolor() != null)
            mRelativeLayout.setBackgroundColor(mLayout.bgcolor());

        for (int i = 0; i < mLayout.items().size(); i++) {
            LayoutXmlParser.Item item = mLayout.items().get(i);
            View view = null;

            if (item instanceof LayoutXmlParser.Button)
                view = addButton((LayoutXmlParser.Button) item);

            if (item instanceof LayoutXmlParser.Space)
                view = addSpace((LayoutXmlParser.Space) item);

            if (item instanceof LayoutXmlParser.Label)
                view = addLabel((LayoutXmlParser.Label) item);

            if (item instanceof LayoutXmlParser.Switch)
                view = addSwitch((LayoutXmlParser.Switch) item);

            if (item instanceof LayoutXmlParser.Joystick)
                view = addJoystick((LayoutXmlParser.Joystick) item);

            if (item instanceof LayoutXmlParser.Slider)
                view = addSlider((LayoutXmlParser.Slider) item);

            if(view != null)
                mViews.add(view);
        }
    }

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Hm10Service.ACTION_DISCONNECTED);
        filter.addAction(Hm10Service.ACTION_NOTIFY_MESSAGE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mHm10ServiceReceiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu()");

        // add the menu only if running the demo
        if(mIsDemo) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.control_menu, menu);
        }

        return true;
    }

    // map sensor values onto slider/joystick ranges
    private int sensor_value(int type, Integer lmax, float vals[]) {
        float angle = vals[0];  // sensor 1
        if (type == 2) angle = vals[1];
        if (type == 3) angle = vals[2];

        // map angle to -180° ...180° range
        if (angle < -180) angle += 360;
        if (angle > 180) angle -= 360;

        // get seekbar range 0..max
        int max = 100;
        if (lmax != null) max = lmax;

        // scale angle to seekbar range
        // limit pitch and roll to 0..90, azimuth to 0..360
        if (type == 1) angle = max / 2 + (angle / 360 * max);
        else angle = max / 2 + (angle / 90 * max);
        if (angle < 0) angle = 0;
        if (angle > max) angle = max;

        return (int) angle;
    }

    final SensorEventListener mEventListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            // Handle the events for which we registered
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    //Log.w(TAG, "Accel:" + event.values.length + ": " +
                    //        event.values[0] + " " +event.values[1] + " " +event.values[2] );
                    mSensorAccel = event.values.clone();
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    //Log.w(TAG, "Mag:" + event.values.length + ": " +
                    //        event.values[0] + " " +event.values[1] + " " +event.values[2] );
                    mSensorMag = event.values.clone();
                    break;
            }

            if (mSensorMag != null && mSensorAccel != null) {
                float[] gravity = new float[9];
                float[] magnetic = new float[9];
                SensorManager.getRotationMatrix(gravity, magnetic, mSensorAccel, mSensorMag);
                float[] values = new float[3];
                SensorManager.getOrientation(gravity, values);

                // map values to degrees
                for(int i=0;i<3;i++) values[i] = (float)(180.0 * values[i] / Math.PI);

                // this is a pretty ugly way to limit the sensor rate to 10hz. We should
                // come up with something nicer
                if(mSensorLimiter == null) {
                    // Log.d(TAG, "AZ: " + values[0] + " PITCH: " + values[1] + " ROLL: " + values[2]);

                    // go over all elements
                    for(LayoutXmlParser.Item item: mSensors) {
                        // walk over all views
                        for(View view: mViews) {
                            if(view.getId() == item.id()) {
                                if(view instanceof SeekBar && item instanceof LayoutXmlParser.Slider) {
                                    LayoutXmlParser.Slider slider = (LayoutXmlParser.Slider)item;

                                    if(slider.sensor() != 0) {
                                        int angle = sensor_value(slider.sensor(), slider.max(), values);

                                        // set value on user element
                                        ((SeekBar) view).setProgress(angle);

                                        // send the value
                                        sendMessage("SLIDER " + slider.id() + " " + angle);
                                    }
                                }
                            }
                        }
                   }

                    // send updated values to all connected elements. But limit the rate to 10 hz
                    mHandler.postDelayed(mSensorLimiter = new Runnable() {
                        @Override public void run() { mSensorLimiter = null; }
                    }, 100);
                }

                mSensorMag = null;
                mSensorAccel = null;
            }
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");
        setContentView(R.layout.activity_control);
        mRelativeLayout = findViewById(R.id.controlLayout);

        setFilters();  // Start listening to broadcast notifications e.g. from Hm10Service

        // Get the Intent that started this activity and extract xml layout
        // and parse it into a physical layout
        Intent intent = getIntent();
        mIsDemo = intent.getBooleanExtra("demo", false);
        mXml = intent.getStringExtra("layout");
        if(mXml != null) {
            mLayout = parse(mXml);
            assembleLayout();

            // finally request state
            sendMessage("STATE");
        }

        // check if sensores are used in this layout
        if(mSensors.size() > 0) {
            mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
            mSensorManager.registerListener(mEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(mEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_NORMAL);
        } else
            Log.d(TAG, "No sensors in this layout");
    }
}