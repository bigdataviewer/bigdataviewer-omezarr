package bdv.img.omezarr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.janelia.saalfeldlab.n5.GsonAttributesParser;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class JsonTest {

    public static JsonObject createDefaultOmero()
    {
        JsonObject omero_object = new JsonObject();
        omero_object.addProperty("version", "0.4");
        return omero_object;
    }

    public static void main(String[] args ) throws IOException {
        final Multiscales M = new Multiscales();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final LinkedHashMap<String,String> m1 = new LinkedHashMap<>();
        m1.put("a","b");
        m1.put("c","d");
        final LinkedHashMap<String,String> m2 = new LinkedHashMap<>();
        m2.put("m2a","aa");
        JsonObject root = new JsonObject();
        root.add("m1", gson.toJsonTree(m1));
        root.add("m2", gson.toJsonTree(m2));
        root.add("m3", gson.toJsonTree(new int[]{1, 2, 3}));
        root.add("empty",new JsonObject());
        root.add("omero",gson.toJsonTree(createDefaultOmero()));
        System.out.println(gson.toJson(root));
        Omero om = new Omero();
        om.id = 1;
        om.name = "kakukk";
        System.out.println(gson.toJson(om));
    }
}
