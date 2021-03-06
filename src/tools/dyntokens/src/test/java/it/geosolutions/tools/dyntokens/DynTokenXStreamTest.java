/*
 *  Copyright (C) 2007 - 2012 GeoSolutions S.A.S.
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
package it.geosolutions.tools.dyntokens;

import com.thoughtworks.xstream.XStream;
import it.geosolutions.geobatch.registry.AliasRegistry;
import it.geosolutions.geobatch.xstream.Alias;
import it.geosolutions.tools.dyntokens.model.DynTokenList;
import it.geosolutions.tools.dyntokens.model.StringDynToken;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author ETj (etj at geo-solutions.it)
 */
public class DynTokenXStreamTest {

    public DynTokenXStreamTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of setBaseToken method, of class DynTokenResolver.
     */
    @Test
    public void testSetBaseToken() {

        String filename = "REP12_20120808_20120811_RFVL.nc.gz";

        StringDynToken token = new StringDynToken();
        token.setName("runtime");
        token.setBase("${FILENAME}");
        token.setRegex(".*_([0-9]{4})([0-9]{2})([0-9]{2})_[0-9]{8}_.*");
        token.setCompose("${1}-${2}-${3} 00:00:00");

        DynTokenList tokenList = new DynTokenList();
        tokenList.add(token);

        DynTokenResolver resolver = new DynTokenResolver(tokenList);
        resolver.setBaseToken("FILENAME", filename);

        AliasRegistry registry = new AliasRegistry();
        registry.putAlias("dynamicTokens", DynTokenList.class);
        registry.putAlias("stringToken", StringDynToken.class);
        registry.putImplicitCollection("list", DynTokenList.class);

        XStream xstream = new XStream();
//        xstream.alias("dynamicTokens", DynTokenList.class);
//        xstream.addImplicitCollection(DynTokenList.class, "list");
//        xstream.alias("stringToken", StringDynToken.class);

        Alias alias = new Alias();
        alias.setAliasRegistry(registry);
        alias.setAliases(xstream);

        String xml = xstream.toXML(tokenList);

        System.out.println("DynTokenList:\n" + xml);


    }

}
