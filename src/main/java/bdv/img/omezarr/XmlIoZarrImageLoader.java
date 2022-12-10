/*-
 * #%L
 * This package provides multi image OME-Zarr support in bigdataviewer.
 * %%
 * Copyright (C) 2022 BigDataViewer developers.
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


import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.export.ProgressWriterConsole;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import org.jdom2.Element;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import bdv.img.omezarr.ZarrImageLoader;
import static mpicbg.spim.data.XmlHelpers.loadPath;
import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo( format = "bdv.multimg.zarr", type = ZarrImageLoader.class )
public class XmlIoZarrImageLoader implements XmlIoBasicImgLoader<ZarrImageLoader>
{
    @Override
    public Element toXml(final ZarrImageLoader imgLoader, final File basePath )
    {
        final Element e_imgloader = new Element( "ImageLoader" );
        e_imgloader.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, "bdv.multimg.zarr" );
        e_imgloader.setAttribute( "version", "1.0" );
        e_imgloader.addContent(XmlHelpers.pathElement("zarr", imgLoader.getBasePath(), null));
        final Element e_zgroups = new Element("zgroups");
        for (final Map.Entry<ViewId, String> ze: imgLoader.getZgroups().entrySet())
        {
            final ViewId vId = ze.getKey();
            final Element e_zgroup = new Element("zgroup");
            e_zgroup.setAttribute("setup", String.valueOf(vId.getViewSetupId()));
            e_zgroup.setAttribute("timepoint", String.valueOf(vId.getTimePointId()));
            final Element e_path = new Element("path");
            e_path.addContent(ze.getValue());
            e_zgroup.addContent(e_path);
            e_zgroups.addContent(e_zgroup);
        }
        e_imgloader.addContent(e_zgroups);
        return e_imgloader;
    }

    @Override
    public ZarrImageLoader fromXml(final Element elem, final File basePath, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
    {
        final File zpath = loadPath( elem, "zarr", basePath );
        final Element zgroupsElem = elem.getChild( "zgroups" );
        final TreeMap<ViewId, String > zgroups = new TreeMap<>();
        // TODO validate that sequenceDescription and zgroups have the same entries
        for ( final Element c : zgroupsElem.getChildren( "zgroup" ) )
        {
            final int timepointId = Integer.parseInt( c.getAttributeValue( "timepoint" ) );
            final int setupId = Integer.parseInt( c.getAttributeValue( "setup" ) );
            final String path = c.getChild( "path" ).getText();
            zgroups.put( new ViewId( timepointId,setupId ), path );
        }

        return new ZarrImageLoader(zpath.getAbsolutePath(), zgroups, sequenceDescription);
    }

    public static void main( String[] args ) throws SpimDataException
    {
//        final String fn = "/home/gkovacs/data/davidf_zarr_dataset.xml";
        final String fn = "/home/gabor.kovacs/data/davidf_zarr_dataset.xml";
//        final String fn = "/home/gabor.kovacs/data/zarr_reader_test_2022-11-16/bdv_zarr_test3.xml";
//        final String fn = "/Users/kgabor/data/davidf_zarr_dataset.xml";
        final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( fn );
        final ViewerImgLoader imgLoader = ( ViewerImgLoader ) spimData.getSequenceDescription().getImgLoader();
        final ViewerSetupImgLoader<?, ?> setupImgLoader = imgLoader.getSetupImgLoader(0);
        int d = setupImgLoader.getImage(0).numDimensions();
        setupImgLoader.getMipmapResolutions();
        BigDataViewer.open(spimData, "BigDataViewer Zarr Example", new ProgressWriterConsole(), ViewerOptions.options());
        System.out.println( "imgLoader = " + imgLoader );
        System.out.println( "setupimgLoader = " + setupImgLoader );
    }
}


