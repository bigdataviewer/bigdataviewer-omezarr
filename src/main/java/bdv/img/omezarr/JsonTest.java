/*-
 * #%L
 * This package provides multi image OME-Zarr support in bigdataviewer.
 * %%
 * Copyright (C) 2022 - 2024 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.img.omezarr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

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
