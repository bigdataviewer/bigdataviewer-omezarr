/*-
 * #%L
 * This package provides multi image OME-Zarr support in bigdataviewer.
 * %%
 * Copyright (C) 2022 - 2023 BigDataViewer developers.
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
