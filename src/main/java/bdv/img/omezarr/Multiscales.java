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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
//import org.janelia.saalfeldlab.n5.GsonAttributesParser;
import org.jcodings.util.Hash;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * Class to store OME-zarr json metadata.
 *
 * In this class most fields store information in the order it appears in the JSON file.
 * {@code axes} follows the t,ch,z,y,x order; but {@code axisList} has the reversed, java order.
 * {@code coordinateTransformations} follows the JSON order, first element is applicable first.
 *
 * Fields that are not defined transient will be serialized by gson.
 *
 * Original code copied from {@code org.embl.mobie.io.ome.zarr.util.OmeZarrMultiscales}
 *
 */
public class Multiscales
{
    // key in json for multiscales
    public static final String MULTI_SCALE_KEY = "multiscales";

    // Serialisation
    public String version = "0.4"; // default version
    public String name;
    public String type;
    public Axis[] axes; // from v0.4+ within JSON
    public Dataset[] datasets;
    public CoordinateTransformation[] coordinateTransformations; // from v0.4+ within JSON

    // Runtime

    // Simply contains the {@code Axes[] axes}
    // but in reversed order to accommodate
    // the Java array ordering of the image data.
    private transient List< Axis > axisList;
    public transient int numDimensions;

    public Multiscales() {
    }

    public static class Dataset {
        public String path;
        public CoordinateTransformation[] coordinateTransformations;
        public void setOMEIdentity(int numDimensions)
        {
            coordinateTransformations=new CoordinateTransformation[]{
            new CoordinateTransformation(numDimensions,true),
             new CoordinateTransformation(numDimensions, false)};
        }
    }

    /**
     * Object to represent a coordinateTransformation in the json metadata
     * Elements in {@code scale} and {@code translation} follow the JSON order of axes.
     */
    public static class CoordinateTransformation {
        public String type;
        public double[] scale;
        public double[] translation;
        public String path;
        CoordinateTransformation(int numDimensions, boolean scale)
        {
            if (scale)
                setIdentityScale(numDimensions);
            else setZeroTranslation(numDimensions);
        }

        public CoordinateTransformation() {

        }

        public void setIdentityScale(int numDimensions)
        {
            type="scale";
            scale=new double[numDimensions];
            Arrays.fill(scale, 1.0);
            translation=null;
            path=null;
        }
        public void setZeroTranslation(int numDimensions)
        {
            type="translation";
            translation=new double[numDimensions];
            Arrays.fill(translation, 0.);
            scale=null;
            path=null;
        }
    }

    public static class Axis
    {
        public static final String CHANNEL_TYPE = "channel";
        public static final String TIME_TYPE = "time";
        public static final String SPATIAL_TYPE = "space";

        public static final String X_AXIS_NAME = "x";
        public static final String Y_AXIS_NAME = "y";
        public static final String Z_AXIS_NAME = "z";

        public String name;
        public String type;
        public String unit;
    }

    /**
     * Initialize {@code axisList} field with the reversed order entries of {@code axes}.
     */
    public void init()
    {
        axisList = new ArrayList<Axis>(axes.length);
        for (int i=axes.length; i-- >0; ) {
            axisList.add(axes[i]);
        }
        numDimensions = axisList.size();
    }

    // TODO Can this be done with a JSONAdapter ?
    public void applyVersionFixes( JsonElement multiscales )
    {
        String version = multiscales.getAsJsonObject().get("version").getAsString();
        if ( version.equals("0.3") ) {
            JsonElement axes = multiscales.getAsJsonObject().get("axes");
            // FIXME
            //   - populate Axes[]
            //   - populate coordinateTransformations[]
            throw new RuntimeException("Parsing version 0.3 not yet implemented.");
        } else if ( version.equals("0.4") ) {
            // This should just work automatically
        } else {
            JsonElement axes = multiscales.getAsJsonObject().get("axes");
            // FIXME
            //   - populate Axes[]
            //   - populate coordinateTransformations[]
            throw new RuntimeException("Parsing version "+ version + " is not yet implemented.");
        }
    }
    /**
     * @return The java index of the channel axis if present. Otherwise -1.
     */
    public int getChannelAxisIndex()
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axisList.get( d ).type.equals( Axis.CHANNEL_TYPE ) )
                return d;
        return -1;
    }

    /**
     * @return The java index of the time axis if present. Otherwise -1.
     */
    public int getTimePointAxisIndex()
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axisList.get( d ).type.equals( Axis.TIME_TYPE ) )
                return d;
        return -1;
    }

    /**
     * @param axisName The name of the axis as defined in the Axis class.
     * @return The java index of the spatial axis if present. Otherwise -1.
     */
    public int getSpatialAxisIndex( String axisName )
    {
        for ( int d = 0; d < numDimensions; d++ )
            if ( axisList.get( d ).type.equals( Axis.SPATIAL_TYPE )
                 && axisList.get( d ).name.equals( axisName ) )
                return d;
        return -1;
    }

    /**
     * Get axes definitions.
     *
     * @return The axis instance in this class. The {@code Axis} items are in the java order.
     */
    public List< Axis > getAxes()
    {
        return axisList;
    }

    /**
     * @param newaxes The new axes definition to be set in this class. The {@code Axis} instances are not copied.
     */
    public void setAxes(Collection<Axis> newaxes)
    {
        axisList = new ArrayList<>();
        axisList.addAll(newaxes);
        numDimensions = axisList.size();
        axes = new Axis[numDimensions];
        for (int i=numDimensions, z=0; i-- >0; ++z) {
            axes[i] = axisList.get(z);
        }
    }

    public void setDatasets(Collection<Dataset> newdatasets)
    {
        datasets=newdatasets.toArray(new Dataset[0]);
    }

    /**
     * Set OME axes definition to represent the given number of spatial and optionally channel and time dimensions.
     *
     * @param spatialDim Number of spatial dimensions.
     * @param cPresent Channel dimension present ?
     * @param tPresent Time dimension present ?
     */
    public void initOMEAxes(int spatialDim, boolean cPresent, boolean tPresent)
    {
        numDimensions = spatialDim;
        List<String> spNames = Arrays.asList( Axis.X_AXIS_NAME, Axis.Y_AXIS_NAME, Axis.Z_AXIS_NAME );
        List<Axis> axDefs = new ArrayList<>();
        for (int i=0; i<numDimensions; i++)
        {
            Axis A = new Axis();
            A.name = spNames.get(i);
            A.type = Axis.SPATIAL_TYPE;
            A.unit = "micrometer";
            axDefs.add(A);
        }
        if (cPresent) {
            Axis A = new Axis();
            A.name = "c";
            A.type = Axis.CHANNEL_TYPE;
            axDefs.add(A);
            ++numDimensions;
        }

        if (tPresent)
        {
            Axis A = new Axis();
            A.name = "t";
            A.type = Axis.TIME_TYPE;
            axDefs.add(A);
            ++numDimensions;
        }
        setAxes(axDefs);
    }

    /**
     * Get the global coordinate transformations of the multiscales section.
     *
     * Note that there are coordinate transformation entries in {@code datasets} that should
     * be applied before these global transformations.
     *
     * @return CoordinateTransformations[]
     */
    public CoordinateTransformation[] getCoordinateTransformations()
    {
        return coordinateTransformations;
    }

    public Dataset[] getDatasets()
    {
        return datasets;
    }

    public int numDimensions()
    {
        return numDimensions;
    }

    public static void main(String[] args ) throws IOException {
        final Multiscales M = new Multiscales();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        final LinkedHashMap<String, JsonElement> map = new LinkedHashMap<>();
        final HashMap<String, Multiscales> m2 = new HashMap<>();
        M.datasets = new Dataset[1];
        M.datasets[0] = new Dataset();

        m2.put(M.MULTI_SCALE_KEY, M);
        GsonAttributesParser.insertAttributes(map, m2, gson);
        final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(System.out));
        GsonAttributesParser.writeAttributes(w, map, gson);
    }
}

