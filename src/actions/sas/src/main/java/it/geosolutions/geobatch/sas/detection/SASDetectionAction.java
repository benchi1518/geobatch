/*
 *  GeoBatch - Open Source geospatial batch processing system
 *  http://geobatch.codehaus.org/
 *  Copyright (C) 2007-2008-2009 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geobatch.sas.detection;

import it.geosolutions.filesystemmonitor.monitor.FileSystemMonitorEvent;
import it.geosolutions.filesystemmonitor.monitor.FileSystemMonitorNotifications;
import it.geosolutions.geobatch.catalog.file.FileBaseCatalog;
import it.geosolutions.geobatch.flow.event.action.Action;
import it.geosolutions.geobatch.flow.event.action.ActionException;
import it.geosolutions.geobatch.flow.event.action.BaseAction;
import it.geosolutions.geobatch.global.CatalogHolder;
import it.geosolutions.geobatch.sas.base.SASUtils;
import it.geosolutions.geobatch.sas.base.SASUtils.FolderContentType;
import it.geosolutions.geobatch.sas.event.SASDetectionEvent;
import it.geosolutions.geobatch.sas.event.SASTrackEvent;
import it.geosolutions.geobatch.task.TaskExecutor;
import it.geosolutions.geobatch.task.TaskExecutorConfiguration;
import it.geosolutions.geobatch.utils.IOUtils;
import it.geosolutions.opensdi.sas.model.Layer;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

/**
 * Action which allows to run a script to convert a detection file to a shapefile
 * and ingest it on geoserver via rest
 *
 * @author Daniele Romagnoli, GeoSolutions
 */

public class SASDetectionAction
        extends BaseAction<FileSystemMonitorEvent>
        implements Action<FileSystemMonitorEvent> {

    /**
     * Default logger
     */
    protected final static Logger LOGGER = Logger.getLogger(SASDetectionAction.class.toString());
    protected final SASDetectionConfiguration configuration;

    public static class ScriptParams {

        public final static String PATH = "shapeGeneratorScript";
        public final static String INPUT = "inputDir";
        public final static String OUTPUT = "outputDir";
        public final static String LOGDIR = "loggingDir";
        public final static String CRS = "crsDefinitionsDir";
    }
    protected final static FileFilter FILEFILTER = createFilter();

    /**
     */
    public SASDetectionAction(SASDetectionConfiguration configuration) {
        super(configuration);
        this.configuration = configuration;
    }

    /**
     * 
     * @return
     */
    public SASDetectionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     *
     */
    public Queue<FileSystemMonitorEvent> execute(Queue<FileSystemMonitorEvent> events)
		throws ActionException {
    	try {
    		listenerForwarder.started();

            // looking for file
            if (events.size() != 1)
                throw new IllegalArgumentException("Wrong number of elements for this action: "+ events.size());

            if (this.configuration == null) {
                throw new IllegalStateException("DataFlowConfig is null.");
            }

            final Queue<FileSystemMonitorEvent> returnEvents = new LinkedList<FileSystemMonitorEvent>();

            final FileSystemMonitorEvent event = events.remove();
            final File inputFile = event.getSource();

            // //
            //
            // Get the directory containing the data from the specified
            // XML file
            //
            // //
            final List<String> detectionDirs = SASUtils.getDataDirectories(inputFile, FolderContentType.DETECTIONS);

            if (detectionDirs==null || detectionDirs.isEmpty()){
            	LOGGER.warning("Unable to find target data location from the specified file: "+inputFile.getAbsolutePath());
            	return events;
            }
            final int nDetections = detectionDirs.size();
            if (LOGGER.isLoggable(Level.INFO))
            	LOGGER.info(new StringBuilder("Found ").append(nDetections).append(" detection").append(nDetections>1?"s":"").toString());

            for (String detection : detectionDirs){
            	String initTime = null;
            	if (LOGGER.isLoggable(Level.INFO))
                	LOGGER.info("Processing Detection: " + detection);

            	final String directory = detection;
	            final File fileDir = new File(directory); //Mission dir
	            if (fileDir != null && fileDir.isDirectory()) {
	                final File[] foundFiles = fileDir.listFiles(FILEFILTER);
	                if (foundFiles != null && foundFiles.length>0){
	                	initTime = SASUtils.setInitTime(directory,2);
	                    final String subDir = buildDetectionsSubDir(initTime, fileDir);
	                    SASDetectionEvent detectionEvent = ingestDetection(fileDir, subDir);
                        returnEvents.add(detectionEvent);
	                }
	            }
	            if (LOGGER.isLoggable(Level.INFO))
	            	LOGGER.info("Done");
            }
            
            final List<String> trackDirs = SASUtils.getDataDirectories(inputFile, FolderContentType.TRACKS);
            
            if (trackDirs!=null && !trackDirs.isEmpty()){
            	final int nTracks = trackDirs.size();
                if (LOGGER.isLoggable(Level.INFO))
                	LOGGER.info(new StringBuilder("Found ").append(nTracks).append(" track").append(nTracks>1?"s":"").toString());

                for (String track : trackDirs){
                	if (LOGGER.isLoggable(Level.INFO))
                    	LOGGER.info("Processing Track: " + track);

                	final String zipFileLocation = track;
    	            final File zipFile = new File(zipFileLocation);
    	            if (zipFile != null && zipFile.exists() && !zipFile.isDirectory()) {
    	            	SASTrackEvent trackEvent = ingestTrack(zipFile);
    	            	if (trackEvent != null)
    	            		returnEvents.add(trackEvent);
    	            }
    	            if (LOGGER.isLoggable(Level.INFO))
    	            	LOGGER.info("Done");
                }
            }
            
            if (LOGGER.isLoggable(Level.INFO))
            	LOGGER.info("End Of processing");

            listenerForwarder.completed();
            return returnEvents;
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.SEVERE))
                LOGGER.log(Level.SEVERE, t.getLocalizedMessage(), t);
            listenerForwarder.failed(t);
            throw new ActionException(this, t.getMessage(), t);
        }
    }

    /**
     * Setup a proper subDirectory path containing the mission time and mission name
     * @param initTime
     * @param fileDir
     * @return
     */
    protected String buildDetectionsSubDir(final String initTime, final File fileDir) {
        StringBuilder sb = new StringBuilder(initTime).append(SASUtils.SEPARATOR);
        String missionName = fileDir.getName();
        sb.append(missionName);
        return sb.toString();
    }

    /**
     * 
     * @param inputDir
     * @param subDir
     * @throws Exception
     */
    protected SASDetectionEvent ingestDetection(final File inputDir, final String subDir) throws Exception {
        final String baseName = FilenameUtils.getBaseName(inputDir.getAbsolutePath());
        // //
        //
        // Prepare a TaskExecutor to run a conversion script on the provided detection input
        // 
        // //
        final TaskExecutor executor = configureExecutor();
        final Queue<FileSystemMonitorEvent> events = new LinkedBlockingQueue<FileSystemMonitorEvent>();
        final String outputDir = new StringBuilder(configuration.getDetectionsOutputDir()).append(SASUtils.SEPARATOR).append(subDir).toString();

        // Generate an XML File containing the script parameters
        final File xmlFile = generateXML(inputDir, outputDir);

        // Invoke the taskExecutor to run the script and convert the detection to a shape file
        FileSystemMonitorEvent fse = new FileSystemMonitorEvent(xmlFile, FileSystemMonitorNotifications.FILE_ADDED);
        events.add(fse);
        executor.execute(events);
        if (events == null) {
            throw new RuntimeException("Task Execution went wrong");
        }

        IOUtils.deleteFile(xmlFile); // FIXME: put this delete() in a finally block

        final String dataPrefix = new StringBuilder(outputDir)
                .append(SASUtils.SEPARATOR).append("target_").append(baseName)
                .append(SASUtils.SEPARATOR).append("target_").append(baseName)
                .toString();

        final String prjFile = new StringBuilder(dataPrefix).append(".prj").toString();

        // Does additional checks on the prj file.
        checkPrj(prjFile);
        File zipFile = zipShape(dataPrefix);

        SASDetectionEvent event = new SASDetectionEvent(zipFile);
        
        Pattern MISSIONPATTERN = Pattern.compile("mission(.*)");
        Matcher m = MISSIONPATTERN.matcher(SASUtils.getMissionName(baseName));
        
        if (m.find()) {
        	event.setMissionName(m.group(1));
        } else {
        	LOGGER.severe("ATTENTION: Unparsable Mission name for Detection Event!");
        }
        
        event.setFormat("shp");
        event.setWmsPath(buildWmsPath(zipFile));
        
        Layer layer = new Layer();
        layer.setName(FilenameUtils.getBaseName(zipFile.getAbsolutePath()));
        layer.setTitle(FilenameUtils.getBaseName(zipFile.getAbsolutePath()));
        layer.setDesctiption("");
        layer.setFileURL(zipFile.getAbsolutePath());
        layer.setNamespace("it.geosolutions");
        layer.setServerURL(null);
        layer.setStyle("detection");
        
        ShapefileDataStore shpDs = new ShapefileDataStore(new File(dataPrefix + ".shp").toURI().toURL());
        ReferencedEnvelope originalEnvelope = shpDs.getFeatureSource().getBounds();
        Integer srsID = CRS.lookupEpsgCode(originalEnvelope.getCoordinateReferenceSystem(), true);
        layer.setNativeCRS(srsID != null ? "EPSG:" + srsID : originalEnvelope.getCoordinateReferenceSystem().toWKT());
        
        WKTReader wktReader = new WKTReader();
        
        // minX minY, maxX minY, maxX maxY, minX maxY, minX minY
        Polygon nativeEnvelope = (Polygon) wktReader.read(
        				 "POLYGON(("+originalEnvelope.getMinX()+" "+originalEnvelope.getMinY()+", " +
        				 ""+originalEnvelope.getMaxX()+" "+originalEnvelope.getMinY()+", " +
        				 ""+originalEnvelope.getMaxX()+" "+originalEnvelope.getMaxY()+", " +
        				 ""+originalEnvelope.getMinX()+" "+originalEnvelope.getMaxY()+", " +
        				 ""+originalEnvelope.getMinX()+" "+originalEnvelope.getMinY()+"))");
        if (srsID != null ) 
        	nativeEnvelope.setSRID(srsID);
		layer.setNativeEnvelope(nativeEnvelope);
		
        layer.setSrs(srsID != null ? "EPSG:" + srsID : "UNKNOWN");
        BoundingBox originalToWgs84Envelope = originalEnvelope.toBounds(CRS.decode("EPSG:4326", true));
        Polygon wgs84Envelope = (Polygon) wktReader.read(
        				 "POLYGON(("+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMinY()+", " +
        				 ""+originalToWgs84Envelope.getMaxX()+" "+originalToWgs84Envelope.getMinY()+", " +
        				 ""+originalToWgs84Envelope.getMaxX()+" "+originalToWgs84Envelope.getMaxY()+", " +
        				 ""+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMaxY()+", " +
        				 ""+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMinY()+"))");

        wgs84Envelope.setSRID(4326);
		layer.setWgs84Envelope(wgs84Envelope);

        event.setLayer(layer);
        
		return event;
    }

    /**
     * 
     * @param shapeFileArchive
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
	protected SASTrackEvent ingestTrack(final File zipFile) throws Exception {
    	final String baseName = FilenameUtils.getBaseName(zipFile.getParentFile().getParentFile().getAbsolutePath());
    	
        SASTrackEvent event = new SASTrackEvent(zipFile);
        
        Pattern MISSIONPATTERN = Pattern.compile("mission(.*)");
        Matcher m = MISSIONPATTERN.matcher(SASUtils.getMissionName(baseName));
        
        if (m.find()) {
        	event.setMissionName(m.group(1));
        } else {
        	LOGGER.severe("ATTENTION: Unparsable Mission name for Detection Event!");
        }

        event.setFormat("shp");
        event.setWmsPath(buildWmsPath(zipFile));
        
        Layer layer = new Layer();
        layer.setFileURL(zipFile.getAbsolutePath());
        layer.setNamespace("it.geosolutions");
        layer.setServerURL(null);
        
        File destDir = new File(File.createTempFile("gbSASDetection", ".tmp").getParentFile(), FilenameUtils.getBaseName(zipFile.getAbsolutePath()));
        destDir.mkdir();
		IOUtils.unzipFlat(zipFile, destDir);
		
		if (destDir != null && destDir.isDirectory() && destDir.listFiles().length > 0) {
			for (String fileName : destDir.list()) {
				if (FilenameUtils.getExtension(fileName).equalsIgnoreCase("shp")) {
					ShapefileDataStore shpDs = new ShapefileDataStore(new File(destDir, FilenameUtils.getBaseName(fileName) + ".shp").toURI().toURL());
			        layer.setName(FilenameUtils.getBaseName(fileName));
			        layer.setTitle(FilenameUtils.getBaseName(fileName));
			        layer.setDesctiption("");
			        ReferencedEnvelope originalEnvelope = shpDs.getFeatureSource().getBounds();
			        Integer srsID = CRS.lookupEpsgCode(originalEnvelope.getCoordinateReferenceSystem(), true);
			        layer.setNativeCRS(srsID != null ? "EPSG:" + srsID : originalEnvelope.getCoordinateReferenceSystem().toWKT());
			        
			        WKTReader wktReader = new WKTReader();
			        
			        // minX minY, maxX minY, maxX maxY, minX maxY, minX minY
			        Polygon nativeEnvelope = (Polygon) wktReader.read(
			        				 "POLYGON(("+originalEnvelope.getMinX()+" "+originalEnvelope.getMinY()+", " +
			        				 ""+originalEnvelope.getMaxX()+" "+originalEnvelope.getMinY()+", " +
			        				 ""+originalEnvelope.getMaxX()+" "+originalEnvelope.getMaxY()+", " +
			        				 ""+originalEnvelope.getMinX()+" "+originalEnvelope.getMaxY()+", " +
			        				 ""+originalEnvelope.getMinX()+" "+originalEnvelope.getMinY()+"))");
			        if (srsID != null ) 
			        	nativeEnvelope.setSRID(srsID);
					layer.setNativeEnvelope(nativeEnvelope);
					
			        layer.setSrs(srsID != null ? "EPSG:" + srsID : "UNKNOWN");
			        BoundingBox originalToWgs84Envelope = originalEnvelope.toBounds(CRS.decode("EPSG:4326", true));
			        Polygon wgs84Envelope = (Polygon) wktReader.read(
			        				 "POLYGON(("+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMinY()+", " +
			        				 ""+originalToWgs84Envelope.getMaxX()+" "+originalToWgs84Envelope.getMinY()+", " +
			        				 ""+originalToWgs84Envelope.getMaxX()+" "+originalToWgs84Envelope.getMaxY()+", " +
			        				 ""+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMaxY()+", " +
			        				 ""+originalToWgs84Envelope.getMinX()+" "+originalToWgs84Envelope.getMinY()+"))");

			        wgs84Envelope.setSRID(4326);
					layer.setWgs84Envelope(wgs84Envelope);

					FeatureType schema = shpDs.getSchema();
			        Class binding = schema.getDescriptor("the_geom").getType().getBinding();
					if (Polygon.class == binding ||
			        	MultiPolygon.class == binding) {
				        layer.setStyle("polygon");			        	
			        } else if (LineString.class == binding ||
			        	MultiLineString.class == binding) {
				        layer.setStyle("line");			        	
			        } else {
				        layer.setStyle("point");
			        }

			        event.setLayer(layer);
			        
					return event;
				}
			}
		}
        
        return null;
    }
    
    /**
     * Produce an XML file containing the configuration for a script to be launched
     * by the Task Executor.
     * @param inputFile 
     * @param initTime 
     * 
     * @return
     * @throws ParserConfigurationException
     * @throws TransformerException
     * @throws IOException
     */
    protected File generateXML(final File inputFile, final String outputDir) throws ParserConfigurationException, TransformerException, IOException {
        final DocumentBuilderFactory dfactory = DocumentBuilderFactory.newInstance();

        //Get the DocumentBuilder
        DocumentBuilder parser = dfactory.newDocumentBuilder();
        //Create blank DOM Document
        Document doc = parser.newDocument();

        Element root = doc.createElement("PythonShapeGenerator");
        doc.appendChild(root);

        Element element = doc.createElement(ScriptParams.PATH);
        root.appendChild(element);

        final File converterPath = IOUtils.findLocation(configuration.getDetectionConverterPath(),
                new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));

        // Add a text node to the beginning of the element
        element.insertBefore(doc.createTextNode(converterPath.getAbsolutePath()), null);

        element = doc.createElement(ScriptParams.INPUT);
        root.appendChild(element);
        element.insertBefore(doc.createTextNode(inputFile.getAbsolutePath()), element.getFirstChild());

        final File outDir = new File(outputDir);
        if (!outDir.exists()) {
            SASUtils.makeDirectories(outputDir);
        }
        element = doc.createElement(ScriptParams.OUTPUT);
        root.appendChild(element);
        element.insertBefore(doc.createTextNode(outputDir), null);

        final String crsDir = configuration.getCrsDefinitionsDir();
        if (crsDir != null && crsDir.trim().length() > 0) {
            final File crsPath = IOUtils.findLocation(crsDir,
                    new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));
            if (crsPath != null && crsPath.exists() && crsPath.isDirectory()) {
                element = doc.createElement(ScriptParams.CRS);
                root.appendChild(element);
                element.insertBefore(doc.createTextNode(crsPath.getAbsolutePath()), null);
            }
        }

        final String logDir = configuration.getLoggingDir();
        if (logDir != null && logDir.trim().length() > 0) {
            final File logPath = IOUtils.findLocation(logDir,
                    new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));
            if (logPath != null && logPath.exists() && logPath.isDirectory()) {
                element = doc.createElement(ScriptParams.LOGDIR);
                root.appendChild(element);
                element.insertBefore(doc.createTextNode(logPath.getAbsolutePath()), null);
            }
        }

        final TransformerFactory factory = TransformerFactory.newInstance();
        final Transformer transformer = factory.newTransformer();
        final File file = File.createTempFile("shapegen", ".xml");
        final Result result = new StreamResult(file);
        final Source xmlSource = new DOMSource(doc);
        transformer.transform(xmlSource, result);
        return file;
    }

    /**
     * Use a proper {@link ShapeFileGeoServerConfigurator} action to send the zipped detection
     * shapefile
     * @param fileToBeSent
     * @throws Exception
     */
//    protected void sendShape(final String fileToBeSent) throws Exception {
//        final GeoServerRESTActionConfiguration gsConfig = new GeoServerRESTActionConfiguration();
//        gsConfig.setGeoserverURL(configuration.getGeoserverURL());
//        gsConfig.setGeoserverUID(configuration.getGeoserverUID());
//        gsConfig.setGeoserverPWD(configuration.getGeoserverPWD());
//        gsConfig.setVectorialLayer(true);
//        gsConfig.setDatatype("shp");
//        gsConfig.setDataTransferMethod(configuration.getGeoserverUploadMethod());
//        gsConfig.setWorkingDirectory(configuration.getWorkingDirectory());
//        gsConfig.setDefaultNamespace(configuration.getDefaultNamespace());
//        gsConfig.setWmsPath(buildWmsPath(fileToBeSent));
//        gsConfig.setDefaultStyle(configuration.getDetectionStyle());
//        GeoServerRESTConfiguratorAction gsGenerator = new GeoServerRESTConfiguratorAction(gsConfig);
//        Queue<FileSystemMonitorEvent> events = new LinkedBlockingQueue<FileSystemMonitorEvent>();
//        FileSystemMonitorEvent fse = new FileSystemMonitorEvent(new File(fileToBeSent), FileSystemMonitorNotifications.FILE_ADDED);
//        events.add(fse);
//        gsGenerator.execute(events);
//    }

    /**
     * Create a zip archive by filling it with a set of file defining a shapefile dataset.
     * @param baseFile
     * @return the zipfile
     * @throws IOException
     */
    protected File zipShape(final String baseFile) throws IOException {
        final String prjFile = new StringBuilder(baseFile).append(".prj").toString();
        final String shapeFile = new StringBuilder(baseFile).append(".shp").toString();
        final String dbfFile = new StringBuilder(baseFile).append(".dbf").toString();
        final String shxFile = new StringBuilder(baseFile).append(".shx").toString();
        final String zipFile = new StringBuilder(baseFile).append(".zip").toString();
        final File outputFile = new File(zipFile);

        final String files[] = new String[]{shapeFile, dbfFile, shxFile, prjFile};
        final byte[] buffer = new byte[4096]; // Create a buffer for copying
        int bytesRead;

        final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputFile));
        try {
            for (String file : files) {
                final File f = new File(file);
                if (f.isDirectory()) {
                    continue; // Ignore directory
                }
                final FileInputStream in = new FileInputStream(f); // Stream to read file
                final ZipEntry entry = new ZipEntry(f.getName()); // Make a ZipEntry
                out.putNextEntry(entry); // Store entry
                try {
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable t) {
                            //Eat me
                        }

                    }
                }
            }
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, e.getLocalizedMessage());
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable t) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, t.getLocalizedMessage());
                    }
                }
            }
        }
        return outputFile;
    }

    /**
     * Check whether the PRJ file contains a real WKT definition or a single line
     * indicating an EPSG code such as EPSG:XXXcode (As an instance: EPSG:32632).
     * In the latter case, rewrite that PRJ with the proper WKT definition.
     * That case may happen when the converting utility didn't successfully find a
     * valid WKT definition to be set for the related shape file.
     *
     * @param prjFile
     */
    protected void checkPrj(final String prjFile) {
        final File prj = new File(prjFile);
        if (prj == null || !prj.exists()) {
            throw new IllegalArgumentException("Prj File is missing: " + prjFile);
        }
        FileInputStream fis = null;
        String epsgCode = null;
        try {
            fis = new FileInputStream(prj);
            byte[] headerEPSG = new byte[12];
            final int len = fis.read(headerEPSG);

            //Checking whether it contains the EPSG code directly instead of
            //a proper WKT
            if (headerEPSG[0] == (byte) 'E'
                    && headerEPSG[1] == (byte) 'P'
                    && headerEPSG[2] == (byte) 'S'
                    && headerEPSG[3] == (byte) 'G'
                    && headerEPSG[4] == (byte) ':') {
                final StringBuilder sb = new StringBuilder("EPSG:");
                for (int i = 5; i < len; i++) {
                    sb.append(headerEPSG[i] - 48);
                }
                epsgCode = sb.toString();

                //Try parsing the provided EPSG code to setup a valid CRS
                final CoordinateReferenceSystem crs = CRS.decode(epsgCode);
                if (crs != null) {

                    String s = crs.toWKT();
                    s = s.replaceAll("\n", "").replaceAll("  ", "");

                    //Write out the proper PRJ file
                    FileWriter out = new FileWriter(prj);
                    try {
                        if (fis != null) {
                            fis.close();
                        }
                    } catch (Throwable t) {
                        //Eat me
                    }

                    try {
                        out.write(s);
                    } finally {
                        out.close();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Unable to decode the provided epsg " + epsgCode, e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to decode the provided epsg " + epsgCode, e);
        } catch (NoSuchAuthorityCodeException e) {
            throw new IllegalArgumentException("Unable to decode the provided epsg " + epsgCode, e);
        } catch (FactoryException e) {
            throw new IllegalArgumentException("Unable to decode the provided epsg " + epsgCode, e);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (Throwable t) {
                //Eat exception
            }
        }
    }

    /**
     * Initialize a {@link TaskExecutor} for the shapefile generation.
     * @return
     * @throws IOException
     */
    protected TaskExecutor configureExecutor() throws IOException {
        final TaskExecutorConfiguration taskConfig = new TaskExecutorConfiguration();

        // //
        //
        // 1) Setting the crsDefintionDir which represents a folder containing WKT defintions
        // available as prj files named "crsXXXX.prj" where XXXX is an EPSG code
        //
        // //
        final String crsDefintionDir = configuration.getCrsDefinitionsDir();
        File crsDefDir = null;
        if (crsDefintionDir != null && crsDefintionDir.trim().length() > 0) {
            crsDefDir = IOUtils.findLocation(crsDefintionDir,
                    new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));
            if (crsDefDir == null || !crsDefDir.exists() || !crsDefDir.isDirectory()) {
                throw new IllegalArgumentException("The provided CRS WKT Definitions folder isn't valid" + crsDefintionDir);
            }
        }

        // //
        //
        // 2) Setting the errorLog file which will contains error occurred during task execution
        //
        // //
        final String errorLog = configuration.getDetectionsErrorLog();
        if (errorLog != null && errorLog.trim().length() > 0) {
            final File errorLogFile = IOUtils.findLocation(errorLog,
                    new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));
            if (errorLogFile != null && errorLogFile.exists()) {
                taskConfig.setErrorFile(errorLogFile.getAbsolutePath());
            }
        }

        // //
        //
        // 3) Setting the xsl path which will contains xsl needed to setup a proper XML file
        //
        // //
        final String xslPath = configuration.getXslPath();
        if (xslPath != null && xslPath.trim().length() > 0) {
            final File xslFile = IOUtils.findLocation(xslPath,
                    new File(((FileBaseCatalog) CatalogHolder.getCatalog()).getBaseDirectory()));
            if (xslFile != null && xslFile.exists()) {
                taskConfig.setXsl(xslFile.getAbsolutePath());
            } else {
                throw new IllegalArgumentException("Invalid xsl file: " + xslPath);
            }
        }

        taskConfig.setExecutable(configuration.getExecutablePath());
        taskConfig.setTimeOut(new Long(configuration.getConverterTimeout()));
        final Map<String, String> variables = new HashMap<String, String>();
        variables.put("GDAL_DATA", configuration.getGdalData());
        variables.put("PATH", configuration.getPath());

        taskConfig.setVariables(variables);

        final TaskExecutor executor = new TaskExecutor(taskConfig);
        return executor;
    }

    protected static IOFileFilter createFilter() {
        IOFileFilter fileFilter = includeFilters(
                FileFilterUtils.suffixFileFilter("mat"));
        return fileFilter;
    }

    protected static IOFileFilter excludeFilters(final IOFileFilter inputFilter,
            IOFileFilter... filters) {
        IOFileFilter retFilter = inputFilter;
        for (IOFileFilter filter : filters) {
            retFilter = FileFilterUtils.andFileFilter(
                    retFilter,
                    FileFilterUtils.notFileFilter(filter));
        }
        return retFilter;
    }

    protected static IOFileFilter includeFilters(final IOFileFilter inputFilter,
            IOFileFilter... filters) {
        IOFileFilter retFilter = inputFilter;
        for (IOFileFilter filter : filters) {
            retFilter = FileFilterUtils.orFileFilter(retFilter, filter);
        }
        return retFilter;
    }

    /**
     * Build a WMSPath from the specified inputFile 
     * Input names are in the form: /DATE/MISSION/target_MISSION/target_completefilename

     * 
     * @param name
     * @return
     */
    protected static String buildWmsPath(final File file) {

        //Will be something like
        //target_MUSCLE_CAT2_091002_1_12_s_6506_6658_40_150_det029_r127_dt032.shp

        //will refer to /MISSIONDIR
        final File missionDir = file.getParentFile().getParentFile();

        String missionName = getMissionName(file);
        
        //will refer to /DATE
        final File timeDir = missionDir.getParentFile();
        String time = FilenameUtils.getBaseName(timeDir.getAbsolutePath());

        final int missionIndex = missionName.lastIndexOf("_");
        if (missionIndex != -1) {
            final String missionCollapsed = missionName.substring(0, missionIndex).replace("_", "-");
            missionName = new StringBuilder("mission").append(missionCollapsed).append(missionName.substring(missionIndex + 1)).toString();
        } else {
            missionName = new StringBuilder("mission").append(missionName).toString();
        }

        final String wmsPath = new StringBuilder("/").append(time).append("/").append(missionName).toString();
        return wmsPath;
    }

    protected static String getMissionName(final File file) {
        //will refer to /MISSIONDIR
        final File missionDir = file.getParentFile().getParentFile();

        String missionName = FilenameUtils.getBaseName(missionDir.getAbsolutePath());

        return missionName;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("DetectionManagerConfiguratorAction [");
        if (configuration != null) {
            builder.append("configuration=").append(configuration);
        }
        builder.append("]");
        return builder.toString();
    }
}