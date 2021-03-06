/*
 *  GeoBatch - Open Source geospatial batch processing system
 *  http://geobatch.geo-solutions.it/
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
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

package it.geosolutions.geobatch.flow.event.listeners.logger;

import it.geosolutions.geobatch.catalog.Descriptable;
import it.geosolutions.geobatch.catalog.Identifiable;
import it.geosolutions.geobatch.flow.event.ProgressListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author ETj <etj at geo-solutions.it>
 * @author (r2)Carlo Cancellieri - carlo.cancellieri@geo-solutions.it
 */
public class LoggingProgressListener extends ProgressListener {

	private final Logger logger;

	
	public LoggingProgressListener(
			LoggingProgressListenerConfiguration configuration,
			Identifiable owner) {
		super(configuration, owner);
		if (configuration == null)
			throw new IllegalArgumentException("Null configuration!");
		logger = LoggerFactory.getLogger(configuration.getLoggerName());
	}

	public LoggingProgressListenerConfiguration getConfig() {
		return (LoggingProgressListenerConfiguration) configuration;
	}

	public Logger getLogger() {
		return logger;
	}

	public void started() {
		logger.info("Started [" + getPrintable(getOwner()) + "]");
	}

	public void progressing() {
		logger.info("Progressing " + getProgress() + "% -- " + getTask() + " ["
				+ getPrintable(getOwner()) + "]");
	}

	public void paused() {
		logger.info("Paused " + getProgress() + "% -- " + getTask() + " ["
				+ getPrintable(getOwner()) + "]");
	}

	public void resumed() {
		logger.info("Resumed " + getProgress() + "% -- " + getTask() + " ["
				+ getPrintable(getOwner()) + "]");
	}

	public void completed() {
		logger.info("Completed [" + getPrintable(getOwner()) + "]");
	}

	public void failed(Throwable exception) {
		logger.info("Failed " + getProgress() + "% -- " + getTask() + " ["
				+ getPrintable(getOwner()) + "]");
	}

	public void terminated() {
		logger.info("Terminated " + getProgress() + "% -- " + getTask() + " ["
				+ getPrintable(getOwner()) + "]");
	}

	static protected String getPrintable(Identifiable i) {
		if (i != null) {
			if (i instanceof Descriptable) {
				Descriptable d = (Descriptable) i;
				return d.getName();
			} else {
				return i.getId();
			}
		} else
			return "-";
	}
}
