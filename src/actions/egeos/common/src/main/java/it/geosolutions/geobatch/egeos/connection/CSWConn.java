/*
 *  Copyright (C) 2007 - 2010 GeoSolutions S.A.S.
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

package it.geosolutions.geobatch.egeos.connection;

import be.kzen.ergorr.interfaces.soap.CswSoapClient;
import be.kzen.ergorr.interfaces.soap.csw.CswClient;
import be.kzen.ergorr.interfaces.soap.csw.ServiceExceptionReport;
import be.kzen.ergorr.model.csw.DeleteType;
import be.kzen.ergorr.model.csw.GetRecordByIdResponseType;
import be.kzen.ergorr.model.csw.GetRecordByIdType;
import be.kzen.ergorr.model.csw.InsertType;
import be.kzen.ergorr.model.csw.QueryConstraintType;
import be.kzen.ergorr.model.csw.TransactionResponseType;
import be.kzen.ergorr.model.csw.TransactionType;
import be.kzen.ergorr.model.ogc.BinaryComparisonOpType;
import be.kzen.ergorr.model.ogc.FilterType;
import be.kzen.ergorr.model.ogc.LiteralType;
import be.kzen.ergorr.model.ogc.PropertyNameType;
import be.kzen.ergorr.model.util.JAXBUtil;
import be.kzen.ergorr.model.util.OFactory;
import java.net.URL;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import org.apache.log4j.Logger;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class CSWConn {
    private final static Logger LOGGER = Logger.getLogger(CSWConn.class);

    private URL serviceURL;

    public CSWConn(URL serviceURL) {
        this.serviceURL = serviceURL;
    }

    public JAXBElement getById(String urn) throws ServiceExceptionReport {
        LOGGER.info("Querying record by ID " + urn);

        CswClient client = new CswSoapClient(serviceURL);
        GetRecordByIdType request = new GetRecordByIdType();
        request.getId().add(urn);

        GetRecordByIdResponseType response = client.getRecordById(request);
        List any = response.getAny();

        if(any.size() > 0) {
            JAXBElement resp0 = (JAXBElement)any.get(0);
            LOGGER.info("Found record " + urn + " as a " + resp0.getDeclaredType().getName());
            return resp0;
        } else {
            LOGGER.info("Record not found :: " + urn);
            return null;
        }
    }

    public TransactionResponseType insert(String xml) {
        CswClient client = new CswSoapClient(serviceURL);

        TransactionType request = new TransactionType();
        InsertType insert = new InsertType();

        request.getInsertOrUpdateOrDelete().add(insert);

        JAXBElement jaxbEl = null;
        try {
            jaxbEl = (JAXBElement) JAXBUtil.getInstance().unmarshall(xml);
        } catch (JAXBException e) {
            LOGGER.error("", e);
            return null;
        }

        insert.getAny().add(jaxbEl);

        TransactionResponseType response = null;
        try {
            response = client.transact(request);
        } catch (ServiceExceptionReport e) {
            LOGGER.error("", e);
            return null;
        }

        try {
            LOGGER.info("Insert operation details: "
                    + JAXBUtil.getInstance().marshallToStr(OFactory.csw.createTransactionResponse(response)));
        } catch (JAXBException e) {
            LOGGER.error("Could not extract operation details: ", e);
        }

        return response;
    }

    public static final String DELETE_EXTRINSIC_OBJECT = "wrs:ExtrinsicObject";
    
    public int delete(String urn, String typeName) {

        // first operand
        PropertyNameType sourceObject = new PropertyNameType();
        sourceObject.getContent().add("/rim:ExtrinsicObject/@id");
        // second operand
        LiteralType sourceObjectValue = new LiteralType();
        sourceObjectValue.getContent().add(urn);

        // setting a generic binary comparison operation
        BinaryComparisonOpType comparisonOp = new BinaryComparisonOpType();
        comparisonOp.getExpression().add(OFactory.ogc.createPropertyName(sourceObject));
        comparisonOp.getExpression().add(OFactory.ogc.createLiteral(sourceObjectValue));

        FilterType filter = new FilterType();
        filter.setComparisonOps(OFactory.ogc.createPropertyIsEqualTo(comparisonOp));

        // setting query constraint
        QueryConstraintType queryConstraint = new QueryConstraintType();
        queryConstraint.setFilter(filter);

        DeleteType operation = new DeleteType();
        operation.setConstraint(queryConstraint);
        operation.setTypeName(typeName);

        TransactionType request = new TransactionType();
        request.getInsertOrUpdateOrDelete().add(operation);
        request.setVerboseResponse(true);

        CswClient client = new CswSoapClient(serviceURL);

        TransactionResponseType response = null;
        try {
            response = client.transact(request);
            int deleted = response.getTransactionSummary().getTotalDeleted().intValue();
            LOGGER.info("Deleted " + deleted + " entries from Registry ");

            try {
                LOGGER.info("Delete operation details: "
                        + JAXBUtil.getInstance().marshallToStr(OFactory.csw.createTransactionResponse(response)));
            } catch (JAXBException e) {
                LOGGER.error("Could not extract operation details: ", e);
            }

            return deleted;
        } catch (ServiceExceptionReport e) {
            LOGGER.error("Error in transaction: ", e);
            return -1;
        }
    }

}
