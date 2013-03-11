/* Copyright 2010 University of Cambridge
 * Licensed under the Educational Community License (ECL), Version 2.0. You may not use this file except in 
 * compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at https://source.collectionspace.org/collection-space/LICENSE.txt
 */
package org.collectionspace.chain.csp.webui.record;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.chain.csp.schema.Field;
import org.collectionspace.chain.csp.schema.Relationship;
import org.collectionspace.chain.csp.schema.Spec;
import org.collectionspace.chain.csp.webui.main.Request;
import org.collectionspace.chain.csp.webui.main.WebMethod;
import org.collectionspace.chain.csp.webui.main.WebMethodWithOps;
import org.collectionspace.chain.csp.webui.main.WebUI;
import org.collectionspace.chain.csp.webui.misc.Generic;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.Storage;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.collectionspace.csp.api.persistence.UnimplementedException;
import org.collectionspace.csp.api.ui.Operation;
import org.collectionspace.csp.api.ui.UIException;
import org.collectionspace.csp.api.ui.UIRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordRead implements WebMethodWithOps {
	private static final Logger log=LoggerFactory.getLogger(RecordRead.class);
	private String base;
	private Record record;

	private RecordSearchList searcher;
	private RecordAuthorities termsused;
	private RecordSearchList relatedObjSearcher;
	private Spec spec;
	private boolean record_type;
	private boolean showbasicinfoonly;
	private boolean authorization_type;
	private Map<String,String> type_to_url=new HashMap<String,String>();
	
	public RecordRead(Record r) { 
		this.base=r.getID();
		this.spec=r.getSpec();
		this.record = r;
		this.showbasicinfoonly = false;
		this.searcher = new RecordSearchList(r,RecordSearchList.MODE_LIST);
		this.termsused = new RecordAuthorities(r);
		record_type=r.isType("record");
		authorization_type=r.isType("authorizationdata");
	}
	public RecordRead(Record r, Boolean showbasicinfoonly) { 
		this.base=r.getID();
		this.spec=r.getSpec();
		this.record = r;
		this.showbasicinfoonly = showbasicinfoonly;
		this.searcher = new RecordSearchList(r,RecordSearchList.MODE_LIST);
		this.termsused = new RecordAuthorities(r);
		record_type=r.isType("record");
		authorization_type=r.isType("authorizationdata");
	}
		

	// Builds an object, that has a map of record type to Array mappings.
	private JSONObject buildRelationsSection(Storage storage, String csid)
			throws ExistException, UnimplementedException,
			UnderlyingStorageException, JSONException, UIException {
		JSONObject recordtypes = new JSONObject();
		JSONObject restrictions = new JSONObject();
		JSONObject out = new JSONObject();
		JSONArray paginations = new JSONArray();
		restrictions.put("src", base + "/" + csid);
		// loop over all procedure/recordtypes
		for (Record thisr : spec.getAllRecords()) {
			if ((thisr.isType("procedure") && !thisr.isType("vocabulary"))
					|| "collection-object".equals(thisr.getID()))
				try {
					this.relatedObjSearcher = new RecordSearchList(thisr,
							RecordSearchList.MODE_SEARCH_RELATED);
					this.relatedObjSearcher.configure(spec);
					JSONObject temp = this.relatedObjSearcher.getResults(null,
							storage, restrictions, "results", csid);
					JSONArray results = temp.getJSONArray("results");
					if (results.length() > 0) {
						out.put(thisr.getWebURL(), results);
					}
				} catch (Exception e) {
					log.warn("Unable to to retrieve results for "
									+ thisr.getSearchURL(), e);
				}
		}
		return out;
	}	

	private JSONArray getUsedBy(String id) throws JSONException{
		String instanceid = "vocab-"+id;
		JSONArray usedByNames = new JSONArray();
		if(spec.hasTermlist(instanceid)){
			Field[] fs = spec.getTermlist(instanceid);
			for(Field f : fs){
				JSONObject jo = new JSONObject();
				jo.put("usedBy", f.getRecord().getWebURL() + ":"+ f.getSelector());
				usedByNames.put(jo);
			}
		}
		return usedByNames;
	}

	private JSONArray getTerms(Storage storage, String vocabtype,String Record, Integer limit) throws JSONException, ExistException, UnimplementedException, UnderlyingStorageException{
		

		JSONArray displayNames = new JSONArray();
	    // Get List
		int resultsize =1;
		int pagenum = 0;
		int pagesize = 200;
		if(limit !=0 && limit < pagesize){
			pagesize = limit;
		}
		while(resultsize >0){
			JSONObject restriction=new JSONObject();
			restriction.put("pageNum", pagenum);
			restriction.put("pageSize", pagesize);
			
			String url = Record+"/"+vocabtype;
			JSONObject data = storage.getPathsJSON(url,restriction);
			if(data.has("listItems")){
				String[] results = (String[]) data.get("listItems");
				/* Get a view of each */
				for(String result : results) {
					//change csid into displayName
					JSONObject namedata = getDisplayNameList(storage,Record,vocabtype,result);
					displayNames.put(namedata);
				}

				Integer total = data.getJSONObject("pagination").getInt("totalItems");
				pagesize = data.getJSONObject("pagination").getInt("pageSize");
				//Integer itemsInPage = data.getJSONObject("pagination").getInt("itemsInPage");
				pagenum = data.getJSONObject("pagination").getInt("pageNum");
				pagenum++;
				//are there more results
				if(total <= (pagesize * (pagenum))){
					break;
				}
				//have we got enough results?
				if(limit !=0 && limit <= (pagesize * (pagenum)) ){
					break;
				}
			}
			else{
				resultsize=0;
			}
		}
		return displayNames;
		
	}
	private JSONObject getDisplayNameList(Storage storage,String auth_type,String inst_type,String csid) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException {
		//should be using cached results from the previous query.
		JSONObject out=storage.retrieveJSON(auth_type+"/"+inst_type+"/"+csid+"/view", new JSONObject());
		return out;
	}
	
	private JSONArray getPermissions(Storage storage,JSONObject activePermissionInfo) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException, UIException{
		final String WORKFLOW_DELETE_RESOURCE_TAIL = WORKFLOW_SUB_RESOURCE+"delete";
		final String WORKFLOW_LOCK_RESOURCE_TAIL = WORKFLOW_SUB_RESOURCE+"lock";
		
		JSONArray set = new JSONArray();
		JSONObject activePermissions = new JSONObject();
		
		//log.info(activePermissionInfo.toString());
		//we are ignoring pagination so this will return the first 40 roles only
		//UI doesn't know what it wants to do about pagination etc
		//mark active roles
		if(activePermissionInfo.has("permission"))
		{
			JSONArray active = activePermissionInfo.getJSONArray("permission");
			for(int j=0;j<active.length();j++){
				if(active.getJSONObject(j).length() != 0){
					activePermissions.put(active.getJSONObject(j).getString("resourceName"),active.getJSONObject(j));
				}
			}
		}

		JSONObject mergedPermissions = new JSONObject();
		
		//get all permissions
		int pageNum = 0;
		JSONObject permrestrictions = new JSONObject();
		permrestrictions.put("queryTerm", "actGrp");
		permrestrictions.put("queryString", "CRUDL");
		// Passing page size 0 gets all the perms in one call.
		permrestrictions.put("pageSize", Integer.toString(pageNum));
		String permbase = spec.getRecordByWebUrl("permission").getID();
		JSONObject returndata = searcher.getJSON(storage,permrestrictions,"items",permbase);

		// While loop since perms do not return pagination info - must call till no more
		//while(returndata.has("items") && returndata.getJSONArray("items").length()>0){
		// Using pageSize=0, we get all perms in one call, so no need to loop
		if(returndata.has("items") && returndata.getJSONArray("items").length()>0){

			//merge active and nonactive
			JSONArray items = returndata.getJSONArray("items");
			for(int i=0;i<items.length();i++){
				JSONObject item = items.getJSONObject(i);
				JSONObject permission = new JSONObject();
				String resourceName = item.getString("summary");
				String resourceNameUI;
				// Need to get baseResource for workflow perms
				int startWorkflowSubResource = resourceName.indexOf(WORKFLOW_SUB_RESOURCE);
				if(startWorkflowSubResource>0){	// Contains the workflow subresource	  
					// Get the base resource that the workflow is related to
					int start = (resourceName.startsWith("/"))?1:0;
					String baseResource = resourceName.substring(start,startWorkflowSubResource);
					resourceNameUI = Generic.ResourceNameUI(spec, baseResource);
				} else {
					resourceNameUI = Generic.ResourceNameUI(spec, resourceName);
				}
				permission.put("resourceName", resourceNameUI);
				String permlevel = "none";
				
				Record recordForPermResource = Generic.RecordNameServices(spec,resourceNameUI);
				
				if((startWorkflowSubResource>0) 
						&& (recordForPermResource != null)) {
					// Handle the lock workflow resource
					if(recordForPermResource.supportsLocking() 
						&& resourceName.endsWith("lock")
						&& activePermissions.has(resourceName)
						&& Generic.PermissionIncludesWritable(
							activePermissions.getJSONObject(resourceName).getString("actionGroup"))) {
						// If we have write or delete perms on the workflow resource, set the permLevel
						// on the base resource.
						// Should be, but UI not ready: permission.put("permission", Generic.LOCK_PERMISSION);
						if(!mergedPermissions.has(resourceNameUI)) {
							// With no other knowledge, assume lock perm means writable
							permission.put("permission", Generic.WRITE_PERMISSION);
							mergedPermissions.put(resourceNameUI, permission);
						} else {
							// We could check it and make sure it makes sense, but we have to trust that the UI has
							// done something reasonable by not combining lock perm with read-only or other silliness.
						}
					}
					// Handle the delete workflow resource
					else if(recordForPermResource.hasSoftDeleteMethod() 
						&& resourceName.endsWith("delete")
						&& activePermissions.has(resourceName)
						&& Generic.PermissionIncludesWritable(
							activePermissions.getJSONObject(resourceName).getString("actionGroup"))) {
						// If we have write or delete perms on the workflow resource, set the permLevel
						// on the base resource.
						permission.put("permission", Generic.DELETE_PERMISSION);
						mergedPermissions.put(resourceNameUI, permission);
					}
					else {
						// Filter these out - no need to model them, as we do not support them
						// This is a performance improvement so we do not have to handle them on
						// update.
					}
				}
				else if(activePermissions.has(resourceName) && !mergedPermissions.has(resourceNameUI)){
					permlevel = Generic.PermissionLevelString(activePermissions.getJSONObject(resourceName).getString("actionGroup"));

					permission.put("permission", permlevel);
					mergedPermissions.put(resourceNameUI, permission);
				}
				else if(!mergedPermissions.has(resourceNameUI)){
					permlevel = "none";

					permission.put("permission", permlevel);
					mergedPermissions.put(resourceNameUI, permission);
				}
			}
			
			//pageNum++;
			//permrestrictions.put("pageNum", Integer.toString(pageNum));
			//returndata = searcher.getJSON(storage,permrestrictions,"items",permbase);
		}
		
		//change soft workflow to main Delete


		//now put the permissions in order...
		String[] recordsweburl = spec.getAllRecordsOrdered();
		
		for(String weburl : recordsweburl){
			if(mergedPermissions.has(weburl)){
				Object value = mergedPermissions.get(weburl);
				set.put(value);
			}
		}
		Iterator rit=mergedPermissions.keys();
		while(rit.hasNext()) {
			String key=(String)rit.next();
			Object value = mergedPermissions.get(key);

			if(!spec.hasRecordByWebUrl(key)){
				set.put(value);
			}
		}
		
		return set;
	}
	
	/* Wrapper exists to decomplexify exceptions: also used inCreateUpdate, hence not private */
	public JSONObject getJSON(Storage storage,String csid) throws UIException {
		JSONObject out=new JSONObject();
		JSONObject restrictions=new JSONObject();

		try {
			if(record_type || authorization_type) {
				JSONObject fields=storage.retrieveJSON(base+"/"+csid,restrictions);
				fields.put("csid",csid); // XXX remove this, subject to UI team approval?
				out.put("csid",csid);
				if(base.equals("role")){
					if(authorization_type){
						JSONObject permissions = storage.retrieveJSON(base+"/"+csid+"/"+"permroles/",restrictions);
						JSONArray allperms = getPermissions(storage,permissions);
						fields.put("permissions",allperms);
						String url = base+"/"+csid+"/"+"accountroles/";
						JSONObject accounts = storage.retrieveJSON(url, restrictions);
						JSONArray usedby = new JSONArray();
						if(accounts.has("account")){
							usedby = accounts.getJSONArray("account");
						}
						fields.put("usedBy",usedby);
					}
				} else if(base.equals("termlist")){
					String shortname = fields.getString("shortIdentifier");
					JSONArray allUsed = getUsedBy(shortname);
					fields.put("usedBys", allUsed);
				} else{
					fields = getHierarchy(storage,fields);
					//fields.put("csid",csid);
					//JSONObject relations=createRelations(storage,csid);
					if(!showbasicinfoonly){
						JSONObject tusd = this.termsused.getTermsUsed(storage, base+"/"+csid, new JSONObject());
						out.put("termsUsed",tusd.getJSONArray("results"));
						JSONObject relations=buildRelationsSection(storage,csid);
						out.put("relations",relations);
					}
				}
				out.put("fields",fields);
				
			} else {
				out=storage.retrieveJSON(base+"/"+csid,restrictions);
			}
		} catch (ExistException e) {
			UIException uiexception =  new UIException(e.getMessage(),e);
			return uiexception.getJSON();
		} catch (UnimplementedException e) {
			UIException uiexception =  new UIException(e.getMessage(),e);
			return uiexception.getJSON();
		} catch (UnderlyingStorageException x) {
			UIException uiexception =  new UIException(x.getMessage(),x.getStatus(),x.getUrl(),x);
			return uiexception.getJSON();
		} catch (JSONException e) {
			throw new UIException("Could not create JSON",e);
		}
		if (out == null) {
			UIException uiexception =  new UIException("No JSON Found");
			return uiexception.getJSON();
		}
		return out;
	}
	
	private JSONObject getHierarchy(Storage storage, JSONObject fields) throws JSONException, ExistException, UnimplementedException, UnderlyingStorageException{
		for(Relationship r: record.getSpec().getAllRelations()){
			if(r.showSiblings()){
				//JSONObject temp = new JSONObject();
				//temp.put("_primary", true);
				JSONArray children = new JSONArray();
				//children.put(temp);
				fields.put(r.getSiblingParent(), children);
				if(fields.has(r.getID())){
					//String broadterm = fields.getString(r.getID());
					String child = r.getSiblingChild();
					if(fields.has(child)){
						String broader = fields.getString(child);
						
						JSONObject restrict=new JSONObject();
						restrict.put("dst",broader);	
						restrict.put("type","hasBroader");	
						JSONObject reldata = storage.getPathsJSON("relations/hierarchical",restrict);
						
						fields.remove(child);
						for(int i=0;i<reldata.getJSONObject("moredata").length();i++){

							String[] reld = (String[])reldata.get("listItems");
							String hcsid = reld[i];
							JSONObject mored = reldata.getJSONObject("moredata").getJSONObject(hcsid);
							//it's name is
							JSONObject siblings = new JSONObject();
							if(!fields.getString("csid").equals(mored.getString("subjectcsid"))){
								siblings.put(child,mored.getString("subjectrefname"));
								children.put(siblings);
							}
						}
					}
					fields.put(r.getSiblingParent(), children);
				}
			}
			//add empty array if necessary
			if(!fields.has(r.getID()) && r.mustExistInSpec()){
				if(r.getObject().equals("n")){
					JSONObject temp = new JSONObject();
					temp.put("_primary", true);
					JSONArray at = new JSONArray();
					at.put(temp);
					fields.put(r.getID(),at);
				}
				else{
					fields.put(r.getID(),"");
				}
			}
		}
		return fields;
	}
	

	
	private void store_get(Storage storage,UIRequest request,String path) throws Exception {
		// Get the data
		try {
			if (record.isType("blob")) {
				JSONObject outputJSON = getJSON(storage,path);
				String content = outputJSON.getString("contenttype");
				String contentDisp = outputJSON.has("contentdisposition")?outputJSON.getString("contentdisposition"):null;
				byte[] bob = (byte[])outputJSON.get("getByteBody"); 
				String getByteBody = bob.toString();
				request.sendUnknown(getByteBody, content, contentDisp);
			} else if(record.getID().equals("output")) {
				//
				// Invoke a report
				//
				ReportUtils.invokeReport(this, storage, request, path);
			} else {
				JSONObject outputJSON = getJSON(storage,path);
				outputJSON.put("csid",path);
				// Write the requested JSON out
				request.sendJSONResponse(outputJSON);
			}
		} catch (JSONException e1) {
			throw new UIException("Cannot add csid",e1);
		} catch (ExistException e) {
			throw new UIException("Cannot add csid",e);
		} catch (UnimplementedException e) {
			throw new UIException("Cannot add csid",e);
		} catch (UnderlyingStorageException e) {
			throw new UIException("Cannot add csid",e);
		}
	}
	
	@Override
	public void run(Object in, String[] tail) throws UIException {
		Request q=(Request)in;
		try {
			store_get(q.getStorage(),q.getUIRequest(),StringUtils.join(tail,"/"));
		} catch (Exception e) {
			throw new UIException(e);
		}
	}

	@Override
	public void configure(WebUI ui,Spec spec) {
		for(Record r : spec.getAllRecords()) {
			type_to_url.put(r.getID(),r.getWebURL());
		}
		this.searcher.configure(spec);
	}
	
	public void configure(Spec spec) {
		configure(null, spec);
	}
	
	@Override
	public Operation getOperation() {
		return Operation.READ;
	}
	
	@Override
	public String getBase() {
		return base;
	}
	
	@Override
	public Spec getSpec() {
		// TODO Auto-generated method stub
		return spec;
	}
}
