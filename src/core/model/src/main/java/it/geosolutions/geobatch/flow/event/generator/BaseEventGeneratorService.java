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

package it.geosolutions.geobatch.flow.event.generator;

import it.geosolutions.geobatch.catalog.impl.BaseService;
import it.geosolutions.geobatch.configuration.event.generator.EventGeneratorConfiguration;

import java.util.EventObject;

/**
 * 
 * @author Ivano Picco
 * 
 * @version $ GeoServerConfiguratorService.java $ Revision: 0.1 $ 12/feb/07 12:07:32
 */
public abstract class BaseEventGeneratorService<EO extends EventObject, EGC extends EventGeneratorConfiguration>
        extends BaseService implements EventGeneratorService<EO, EGC> {

    public BaseEventGeneratorService(String id, String name, String description) {
        super(id, name, description);
    }

}