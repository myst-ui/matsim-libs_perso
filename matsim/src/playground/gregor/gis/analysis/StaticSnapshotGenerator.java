/* *********************************************************************** *
 * project: org.matsim.*
 * StaticSnapshotGenerator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.gregor.gis.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.geotools.factory.FactoryRegistryException;
import org.geotools.feature.AttributeType;
import org.geotools.feature.AttributeTypeFactory;
import org.geotools.feature.DefaultAttributeTypeFactory;
import org.geotools.feature.Feature;
import org.geotools.feature.FeatureType;
import org.geotools.feature.FeatureTypeFactory;
import org.geotools.feature.IllegalAttributeException;
import org.geotools.feature.SchemaException;
import org.matsim.events.Events;
import org.matsim.events.EventsReaderTXTv1;
import org.matsim.events.LinkEnterEvent;
import org.matsim.events.handler.LinkEnterEventHandler;
import org.matsim.gbl.Gbl;
import org.matsim.interfaces.core.v01.Coord;
import org.matsim.interfaces.core.v01.Link;
import org.matsim.network.MatsimNetworkReader;
import org.matsim.network.NetworkLayer;
import org.matsim.utils.geometry.geotools.MGC;
import org.matsim.utils.geometry.transformations.TransformationFactory;
import org.matsim.utils.gis.ShapeFileWriter;
import org.matsim.utils.misc.Time;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import playground.gregor.gis.convexer.Concaver;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public class StaticSnapshotGenerator implements LinkEnterEventHandler {
	
	private static final String CVSROOT = "../vsp-cvs";
	private final static Logger log = Logger.getLogger(StaticSnapshotGenerator.class);
	private final static double SNAPSHOT_PERIOD = 15 * 60;
	
	private final static double MAX_X = 657313;
	private final static double MAX_Y = 9901062;
	private final static double MIN_X = 650623;
	private final static double MIN_Y = 9892835;
	
	
	private final NetworkLayer network;
	private final String eventsFile;
	private double oldTime = 3 * 3600;
	
	private final HashMap<String,Link> agentsOnLink = new HashMap<String, Link>();
	private final String shapeFilePrefix;
	private final GeometryFactory geofac;
	private final CoordinateReferenceSystem targetCRS;
	private FeatureType ft;
	private final Concaver concaver;
	
	public StaticSnapshotGenerator(final NetworkLayer network, final String inputFile, final String shapeFilePrefix, final CoordinateReferenceSystem targetCRS) {
		this.network = network;
		this.eventsFile = inputFile;
		this.shapeFilePrefix = shapeFilePrefix;
		this.geofac = new GeometryFactory();
		this.targetCRS = targetCRS;
		
		this.concaver = new Concaver();
		initFeatureCollection();
	}

	
	private void initFeatureCollection() {

		AttributeType geom = DefaultAttributeTypeFactory.newAttributeType("Polygon",Polygon.class, true, null, null, this.targetCRS);
		AttributeType dblTime = AttributeTypeFactory.newAttributeType("dblTime", Double.class);
		AttributeType strgTime = AttributeTypeFactory.newAttributeType("strgTime", String.class);
		
		Exception ex;
		try {
			this.ft = FeatureTypeFactory.newFeatureType(new AttributeType[] {geom, dblTime, strgTime}, "EvacZone");
			return;
		} catch (FactoryRegistryException e) {
			ex = e;
		} catch (SchemaException e) {
			ex = e;
		}
		throw new RuntimeException(ex);
	}

	public void run(){
		this.agentsOnLink.clear();
		Events events = new Events();
		events.addHandler(this);
		new EventsReaderTXTv1(events).readFile(this.eventsFile);
	}
	
	


	public void handleEvent(final LinkEnterEvent event) {
		double time = event.getTime();
		if (time > this.oldTime + SNAPSHOT_PERIOD) {
			this.oldTime = time;
			doSnapshot();
		}
		String agentId = event.agentId;
		Link link = this.network.getLink(event.linkId);
		this.agentsOnLink.put(agentId, link);
		
	}


	private void doSnapshot() {
		Collection<Feature> ft = new ArrayList<Feature>();
		
		String time = Time.writeTime(this.oldTime,'-');
		String fileName = this.shapeFilePrefix + time + ".shp";
		MultiPoint mp = getMultiPoint();
		Polygon p = this.concaver.getConcaveHull(mp);
		try {
			ft.add(this.ft.create(new Object [] {p,this.oldTime,time}));
		} catch (IllegalAttributeException e) {
			throw new RuntimeException(e);
		}
		try {
			ShapeFileWriter.writeGeometries(ft, fileName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	private MultiPoint getMultiPoint() {
		HashSet<Link> links = new HashSet<Link>();
		for (Link link : this.agentsOnLink.values()) {
			if (isInBoundingBox(link.getCoord())) {
				links.add(link);
			}
		}
		Point [] points = new Point[links.size()];
		int c = 0;
		for (Link link : links) {
			points[c++] = MGC.coord2Point(link.getCoord());
		}

		return this.geofac.createMultiPoint(points);
	}


	public void reset(final int iteration) {
		// TODO Auto-generated method stub
		
	}
	
	public boolean isInBoundingBox(final Coord c) {
		if (c.getX() < MIN_X || c.getX() > MAX_X) {
			return false;
		}
		
		if (c.getY() < MIN_Y || c.getY() > MAX_Y) {
			return false;
		}
		
		return true;
	}
	
	public static void main(final String [] args) {
		
		if (args.length != 1) {
			throw new RuntimeException("Error using StaticSnapshotGenerator!\n\tUsage:StaticSnapshotGenerator /path/to/configFile");
		}
		String config = args[0];
		Gbl.createConfig(new String [] {config});
		
		final String shapeFilePrefix =  CVSROOT + "/runs/run314/qgis/evacProgress";
		
		NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(Gbl.getConfig().network().getInputFile());
		
		final CoordinateReferenceSystem targetCRS = MGC.getCRS(TransformationFactory.WGS84_UTM47S);
		
		new StaticSnapshotGenerator(network,Gbl.getConfig().events().getInputFile(),shapeFilePrefix,targetCRS).run();
	}
}
