package bdv.img.omezarr;

// Omero metadata class
public class Omero {
    public static final String OMERO_KEY = "omero";

    public static class Channel {
        public static class Window {
            public float end = 1.0F;
            public float max = 1.0F;
            public float min = 0.0F;
            public float start = 0.0F;
        };
        public boolean active = true;
        int coefficient = 1;
        public String color = "000000";
        public String family = "linear";
        public boolean inverted = false;
        public String label = "";
        public Window window = new Window();
        }

    public static class Rdef {
        public int defaultT = 0;
        public int defaultZ = 0;
        public String model = "color";
    }

    public Omero.Channel[] channel;
    public int id;
    public String name;
    public Rdef[] rdefs;
    public String version = "0.4";
}
