package org.harbaum.ftduinoblue;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.util.Xml;
import android.widget.RelativeLayout;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutXmlParser {
    private static final String ns = null;
    private float mScale = 1;

    public void setScale(float scale) { mScale = scale; }

    public static class Layout {
        private String mName;
        private int mOrientation;
        private Integer mBgColor = null;
        private List<Item> mEntries;
        private float mScale = 1;

        public Layout() {
            mName = "Control";
            mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            mEntries = new ArrayList<>();
        }

        public void parseAttributes(XmlPullParser parser) {
            String name = parser.getAttributeValue(null,"name");
            if(name != null) mName = name;
            String orientation = parser.getAttributeValue(null,"orientation");
            if(orientation != null) setOrientation(orientation);
            String bgcolor = parser.getAttributeValue(null, "bgcolor");
            if(bgcolor != null) { mBgColor = Color.parseColor(bgcolor); }
        }

        public void setOrientation(String orientation) {
            // portrait is default
            if (orientation.equalsIgnoreCase("landscape"))
                mOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            if (orientation.equalsIgnoreCase("sensor"))
                mOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }

        public String name() {
            return mName;
        }
        public int orientation() {
            return mOrientation;
        }
        public List<Item> items() {
            return mEntries;
        }
        public Integer bgcolor() {
            return mBgColor;
        }
    }

    public Layout parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            return readLayout(parser);
        } finally {
            in.close();
        }
    }

    private Layout readLayout(XmlPullParser parser) throws XmlPullParserException, IOException {
        Layout layout = new Layout();

        parser.require(XmlPullParser.START_TAG, ns, "layout");
        layout.parseAttributes(parser);

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName().toLowerCase();
            if ((name.equalsIgnoreCase("button")) ||
                (name.equalsIgnoreCase("space")) ||
                (name.equalsIgnoreCase("switch")) ||
                (name.equalsIgnoreCase("label")) ||
                (name.equalsIgnoreCase("joystick")) ||
                (name.equalsIgnoreCase("slider"))) {
                Item item = readItem(parser, name);
                if(item != null)
                    layout.mEntries.add(item);
            }
            else {
                skip(parser);
            }
        }
        return layout;
    }

    public static class Item {
        int mId;
        float mScale = 1;
        Integer mBgColor = null;
        Integer mColor = null;
        RelativeLayout.LayoutParams mParam;
        Integer mSize = null;
        boolean mEnabled = true;

        private Item(int id) {
            mId = id;
            mParam = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        }

        public void setScale(float scale) {
            mScale = scale;

            // set margins once we know the scale factor
            int px = scale(2);
            mParam.setMargins(px,px,px,px);
        }

        private int scale(float v) {
            return (int)(v * mScale + 0.5f);
        }

        public void parseText(String s) { }

        private int parseLength(String s) {
            if(s.toLowerCase().equals("content"))
                return RelativeLayout.LayoutParams.WRAP_CONTENT;
            if(s.toLowerCase().equals("parent"))
                return RelativeLayout.LayoutParams.MATCH_PARENT;

            return scale(Integer.parseInt(s));
        }

        public void parseAttributes(XmlPullParser parser) {
            // parse item placements if present
            String place = parser.getAttributeValue(null, "place");
            if(place != null) parseplacement(place);
            String width = parser.getAttributeValue(null, "width");
            if(width != null) mParam.width = parseLength(width);
            String height = parser.getAttributeValue(null, "height");
            if(height != null) mParam.height = parseLength(height);
            try {
                String color = parser.getAttributeValue(null, "color");
                if (color != null) mColor = Color.parseColor(color);
            } catch(IllegalArgumentException e) { /*ignore */ }
            try {
                String bgcolor = parser.getAttributeValue(null, "bgcolor");
                if (bgcolor != null) mBgColor = Color.parseColor(bgcolor);
            } catch(IllegalArgumentException e) { /* ignore */ }
            String size = parser.getAttributeValue(null, "size");
            if(size != null) { mSize = scale(Integer.parseInt(size)); }
            String enabled = parser.getAttributeValue(null, "enabled");
            if(enabled != null && enabled.equalsIgnoreCase("false"))
                mEnabled = false;
        }

        public RelativeLayout.LayoutParams layoutParams() {
            return mParam;
        }

        private
        void parseplacement(String placeStr) {
            // a placement looks like "below:1;left_of:1"
            String[] placeItems = placeStr.split(";");
            for (String placeItem : placeItems) {
                final Map<String , Integer> simpleLayout = new HashMap<String , Integer>() {{
                    put("center",        RelativeLayout.CENTER_IN_PARENT);
                    put("hcenter",       RelativeLayout.CENTER_HORIZONTAL);
                    put("vcenter",       RelativeLayout.CENTER_VERTICAL);

                    put("top",           RelativeLayout.ALIGN_PARENT_TOP);
                    put("bottom",        RelativeLayout.ALIGN_PARENT_BOTTOM);
                    put("start",         RelativeLayout.ALIGN_PARENT_START);
                    put("end",           RelativeLayout.ALIGN_PARENT_END);
                    put("left",          RelativeLayout.ALIGN_PARENT_LEFT);
                    put("right",         RelativeLayout.ALIGN_PARENT_RIGHT);
                }};

                final Map<String , Integer> relativeLayout = new HashMap<String , Integer>() {{
                    put("above",          RelativeLayout.ABOVE);
                    put("below",          RelativeLayout.BELOW);
                    put("left_of",        RelativeLayout.LEFT_OF);
                    put("right_of",       RelativeLayout.RIGHT_OF);
                    put("start_of",       RelativeLayout.START_OF);
                    put("end_of",         RelativeLayout.END_OF);

                    put("align_top",      RelativeLayout.ALIGN_TOP);
                    put("align_bottom",   RelativeLayout.ALIGN_BOTTOM);
                    put("align_start",    RelativeLayout.ALIGN_START);
                    put("align_end",      RelativeLayout.ALIGN_END);
                    put("align_left",     RelativeLayout.ALIGN_LEFT);
                    put("align_right",    RelativeLayout.ALIGN_RIGHT);

                    put("align_baseline", RelativeLayout.ALIGN_BASELINE);
                }};

                String[] parts = placeItem.split(":", 2);
                int placeId = (parts.length > 1)?Integer.parseInt(parts[1]):-1;

                if(simpleLayout.containsKey(parts[0])) {
                    Integer val = simpleLayout.get(parts[0]);
                    if(val != null) mParam.addRule(val);
                }

                else if(relativeLayout.containsKey(parts[0]) && (placeId > 0)) {
                    Integer val = relativeLayout.get(parts[0]);
                    if(val != null) mParam.addRule(val, placeId);
                }
            }
        }

        public Integer size() { return mSize; }
        public boolean enabled() { return mEnabled; }
        public int id() { return mId; }
        public Integer color() { return mColor; }
        public Integer bgcolor() { return mBgColor; }
    }

    public static class Space extends Item {
        private Space(int id) { super(id); }
    }

    public static class Button extends Item {
        String mText = null;

        private Button(int id) {
            super(id);
        }

        public void parseText(String s) {
            mText = s;
        }

        public String text() {
            return mText;
        }
    }

    public static class Switch extends Item {
        String mText = null;

        private Switch(int id) {
            super(id);
        }

        public void parseText(String s) {
            mText = s;
        }

        public String text() {
            return mText;
        }
    }

    public static class Label extends Item {
        String mText = null;

        private Label(int id) {
            super(id);
        }

        public void parseText(String s) {
            mText = s;
        }

        public String text() {
            return mText;
        }
    }

    public static class Joystick extends Item {
        int mMode = 0;    // 0 = x/y, 1 = angle, 2=drive
        private int mSensor[] = { 0, 0 };   // x/y 0=none,1=azimuth,2=pitch,3=roll

        private Joystick(int id) {
            super(id);
        }

        public void parseAttributes(XmlPullParser parser) {
            super.parseAttributes(parser);

            // parse item placements if present
            String mode = parser.getAttributeValue(null, "mode");
            if (mode != null) {
                if (mode.equalsIgnoreCase("xy"))    mMode = 0;
                if (mode.equalsIgnoreCase("angle")) mMode = 1;
                if (mode.equalsIgnoreCase("drive")) mMode = 2;
            }
            String sensor = parser.getAttributeValue(null, "sensor");
            if (sensor != null && sensor.split(";").length == 2) {
                // expect two semicolon sperated parts
                int idx = 0;
                for(String s: sensor.split(";")) {
                    if (s.trim().equalsIgnoreCase("azimuth")) mSensor[idx] = 1;
                    if (s.trim().equalsIgnoreCase("pitch"))   mSensor[idx] = 2;
                    if (s.trim().equalsIgnoreCase("roll"))    mSensor[idx] = 3;
                    idx++;
                }
            }
        }
        public Integer mode() { return mMode; }
        public int sensor(int s) { return mSensor[s]; }
    }

    public static class Slider extends Item {
        private Integer mMax = null;
        private Boolean mVertical = false;
        private int mSensor = 0;   // 0=none,1=azimuth,2=pitch,3=roll

        private Slider(int id) {
            super(id);
        }

        public void parseAttributes(XmlPullParser parser) {
            super.parseAttributes(parser);

            // parse item placements if present
            String max = parser.getAttributeValue(null, "max");
            if (max != null) mMax = Integer.parseInt(max);
            String vert = parser.getAttributeValue(null, "orientation");
            if (vert != null) mVertical = vert.trim().equalsIgnoreCase("vertical");
            String sensor = parser.getAttributeValue(null, "sensor");
            if (sensor != null) {
                if(sensor.trim().equalsIgnoreCase("azimuth")) mSensor = 1;
                if(sensor.trim().equalsIgnoreCase("pitch"))   mSensor = 2;
                if(sensor.trim().equalsIgnoreCase("roll"))    mSensor = 3;
            }
        }

        public Integer max() { return mMax; }
        public Boolean vertical() { return mVertical; }
        public int sensor() { return mSensor; }
    }

    private Item readItem(XmlPullParser parser, String element) throws XmlPullParserException, IOException {
        Item item = null;

        parser.require(XmlPullParser.START_TAG, ns, element);
        String idStr = parser.getAttributeValue(null, "id");

        // only create object with a valid id
        if(idStr != null) {
            int id = Integer.parseInt(idStr);

            // create a matching object
            if (element.equals("button"))    item = new Button(id);
            if (element.equals("space"))     item = new Space(id);
            if (element.equals("label"))     item = new Label(id);
            if (element.equals("switch"))    item = new Switch(id);
            if (element.equals("joystick"))  item = new Joystick(id);
            if (element.equals("slider"))    item = new Slider(id);

            if(item != null) {
                // set global scale factor to make sure items can scale
                // themselves correctly
                item.setScale(mScale);

                item.parseAttributes(parser);
                item.parseText(readText(parser));
            } else
                skip(parser);
        }

        parser.require(XmlPullParser.END_TAG, ns, element);

        return item;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}