package org.collectionspace.chain.csp.persistence.services.vocab;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.collectionspace.bconfigutils.bootstrap.BootstrapConfigController;
import org.collectionspace.chain.controller.ChainServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mortbay.jetty.testing.HttpTester;
import org.mortbay.jetty.testing.ServletTester;

public class TestOrgThroughWebapp {
	// XXX refactor
	protected InputStream getResource(String name) {
		String path=getClass().getPackage().getName().replaceAll("\\.","/")+"/"+name;
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
	}
	
	
	// XXX refactor
	private static HttpTester jettyDo(ServletTester tester,String method,String path,String data) throws IOException, Exception {
		HttpTester request = new HttpTester();
		HttpTester response = new HttpTester();
		request.setMethod(method);
		request.setHeader("Host","tester");
		request.setURI(path);
		request.setVersion("HTTP/1.0");		
		if(data!=null)
			request.setContent(data);
		response.parse(tester.getResponses(request.generate()));
		return response;
	}
	
	// XXX refactor into other copy of this method
	private static ServletTester setupJetty() throws Exception {
		BootstrapConfigController config_controller=new BootstrapConfigController(null);
		config_controller.addSearchSuffix("test-config-loader2.xml");
		config_controller.go();
		String base=config_controller.getOption("services-url");		
		ServletTester tester=new ServletTester();
		tester.setContextPath("/chain");
		tester.addServlet(ChainServlet.class, "/*");
		tester.addServlet("org.mortbay.jetty.servlet.DefaultServlet", "/");
		tester.setAttribute("storage","service");
		tester.setAttribute("store-url",base+"/cspace-services/");	
		tester.setAttribute("config-filename","default.xml");
		tester.start();
		return tester;
	}
	
	@BeforeClass public static void reset() throws Exception {
		ServletTester jetty=setupJetty();
		jettyDo(jetty,"GET","/chain/quick-reset",null);		
	}
		
	@Test public void testAuthoritiesSearch() throws Exception {
		ServletTester jetty=setupJetty();
		HttpTester out=jettyDo(jetty,"GET","/chain/authorities/organization/search?query=National+Mask+%26+Puppet+Corp.",null);
		assertTrue(out.getStatus()<299);
		System.err.println(out.getContent());
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		assertEquals(1,results.length());
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			assertTrue(entry.getString("displayName").toLowerCase().contains("national mask & puppet corp."));
			assertEquals(entry.getString("number"),entry.getString("displayName"));
			assertTrue(entry.has("refid"));
		}
	}

	@Test public void testAuthoritiesList() throws Exception {
		ServletTester jetty=setupJetty();
		HttpTester out=jettyDo(jetty,"GET","/chain/authorities/organization",null);
		assertTrue(out.getStatus()<299);
		System.err.println(out.getContent());
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("items");
		boolean found=false;
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			if(entry.getString("displayName").toLowerCase().contains("national mask & puppet corp."))
				found=true;
		}
		assertTrue(found);
	}

	@Test public void testNamesSearch() throws Exception {
		ServletTester jetty=setupJetty();
		jettyDo(jetty,"GET","/chain/quick-reset",null);
		HttpTester out=jettyDo(jetty,"GET","/chain/vocabularies/organization/search?query=National+Mask+%26+Puppet+Corp.",null);
		assertTrue(out.getStatus()<299);
		System.err.println(out.getContent());
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			assertTrue(entry.getString("displayName").toLowerCase().contains("national mask & puppet corp."));
			assertEquals(entry.getString("number"),entry.getString("displayName"));
			assertTrue(entry.has("refid"));
		}
	}

	@Test public void testNamesList() throws Exception {
		ServletTester jetty=setupJetty();
		HttpTester out=jettyDo(jetty,"GET","/chain/vocabularies/organization",null);
		assertTrue(out.getStatus()<299);
		System.err.println(out.getContent());
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("items");
		boolean found=false;
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			if(entry.getString("displayName").toLowerCase().contains("national mask & puppet corp."))
				found=true;
		}
		assertTrue(found);
	}

	@Test public void testNamesGet() throws Exception {
		ServletTester jetty=setupJetty();
		HttpTester out=jettyDo(jetty,"GET","/chain/vocabularies/organization/search?query=National+Mask+%26+Puppet+Corp.",null);
		assertTrue(out.getStatus()<299);
		System.err.println(out.getContent());
		// Find candidate
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		assertEquals(1,results.length());
		JSONObject entry=results.getJSONObject(0);
		String csid=entry.getString("csid");
		out=jettyDo(jetty,"GET","/chain/vocabularies/organization/"+csid,null);
		JSONObject fields=new JSONObject(out.getContent()).getJSONObject("fields");
		System.err.println(fields);
		assertEquals(csid,fields.getString("csid"));
		assertEquals("National Mask & Puppet Corp.",fields.getString("displayName"));
	}

	@Test public void testNamesCreateUpdateDelete() throws Exception {
		ServletTester jetty=setupJetty();
		// Create
		JSONObject data=new JSONObject("{'fields':{'displayName':'National Mask & Puppet Corp.'}}");
		HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());		
		assertTrue(out.getStatus()<300);
		String url=out.getHeader("Location");
		// Read
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		data=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(data.getString("csid"),url.split("/")[2]);
		assertEquals("National Mask & Puppet Corp.",data.getString("displayName"));
		// Update
		data=new JSONObject("{'fields':{'displayName':'Dic Penderyn'}}");
		out=jettyDo(jetty,"PUT","/chain/vocabularies"+url,data.toString());		
		assertTrue(out.getStatus()<300);
		// Read
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		data=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(data.getString("csid"),url.split("/")[2]);
		assertEquals("Dic Penderyn",data.getString("displayName"));
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());		
	}
}
