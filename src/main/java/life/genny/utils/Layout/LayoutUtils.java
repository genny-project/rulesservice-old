package life.genny.utils.Layout;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import life.genny.qwanda.Answer;
import life.genny.qwanda.Layout;
import life.genny.qwanda.attribute.Attribute;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QBulkMessage;
import life.genny.qwanda.message.QDataBaseEntityMessage;
import life.genny.qwandautils.JsonUtils;
import life.genny.qwandautils.QwandaUtils;
import life.genny.utils.BaseEntityUtils;
import life.genny.utils.RulesUtils;
import life.genny.utils.VertxUtils;

import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdSubLayoutMessage;
import life.genny.qwanda.message.QCmdTabViewMessage;
import life.genny.qwanda.message.QCmdViewMessage;
import life.genny.qwanda.message.QCmdViewTableMessage;
import life.genny.qwanda.message.QCmdTableMessage;
import life.genny.qwanda.message.QCmdViewMessageAction;

public class LayoutUtils {

	private Map<String, Object> decodedMapToken;
	private String token;
	private String realm;
	private String qwandaServiceUrl;
	public BaseEntityUtils baseEntityUtils;

	public LayoutUtils(String qwandaServiceUrl, String token, Map<String, Object> decodedMapToken, String realm) {

		this.decodedMapToken = decodedMapToken;
		this.qwandaServiceUrl = qwandaServiceUrl;
		this.token = token;
		this.realm = realm;
		this.baseEntityUtils = new BaseEntityUtils(this.qwandaServiceUrl, this.token, decodedMapToken, realm);

	}

	public List<BaseEntity> getAllLayouts() { 

		String realmCode = this.realm;
		String token = this.token;

		if (realmCode == null) {
			System.out.println("No realm code was provided. Not getting layouts. ");
			return null;
		}

		if (token == null) {
			System.out.println("No token was provided. Not getting layouts.");
			return null;
		}

		List<Layout> layouts = new ArrayList<Layout>();

		/* we grab all the layouts */
		layouts.addAll(this.processLayouts("genny-new"));
		layouts.addAll(this.processLayouts(realmCode + "-new"));

		return layouts.stream().map(layout -> this.baseEntityUtils.baseEntityForLayout(realmCode, token, layout))
				.collect(Collectors.toList());
	}

	public List<Layout> processLayouts(final String realmCode) {
		return processLayouts(realmCode, null);
	}

	public List<Layout> processLayouts(final String realmCode, String subpath) {

		List<Layout> layouts = new ArrayList<Layout>();

		if (subpath == null)
			subpath = "";

		String pathToLayout = subpath; 

		String subLayoutMap = RulesUtils.getLayout(realmCode, pathToLayout);
		System.out.println("Downloading layouts: " + pathToLayout);

		if (subLayoutMap != null) {

			JsonArray subLayouts = null;

			try {

				subLayouts = new JsonArray(subLayoutMap);
				if (subLayouts != null) {

					for (int i = 0; i < subLayouts.size(); i++) {

						JsonObject sublayoutData = null;

						try {
							sublayoutData = subLayouts.getJsonObject(i);

						} catch (Exception e1) {
							e1.printStackTrace();
						}

						if (sublayoutData != null && sublayoutData.containsKey("path")) {

							/* we get the download_url of the current file */
							String download_url = sublayoutData.getString("path");
							String file_path = download_url.replace(realmCode + "/", "");

							/* if we have found a file we serialize it */
							if (file_path.endsWith(".json")) {
								layouts.add(this.serializeLayout(realmCode, sublayoutData));
							} else {

								/* if we have found a folder we recursively download the data inside of it */
								System.out.println("Found subfolder: " + file_path);
								layouts.addAll(this.processLayouts(realmCode, file_path));
							}
						}
					}
				}
			} catch (Exception e) {

			}
		}

		return layouts;
	}

	public static String downloadLayoutContent(Layout layout) {

		String content = null;

		if (layout.getDownloadUrl() != null) {

			try {
				content = QwandaUtils.apiGet(layout.getDownloadUrl(), null);
			} catch (Exception e) {
				System.out.println("Error downloading: " + layout.getDownloadUrl());
			}
		}

		return content;
	}

	private Layout serializeLayout(final String realmCode, JsonObject layoutData) {

		/* serialize layout */
		Gson gson = new Gson();

		Layout newLayout = gson.fromJson(layoutData.toString(), Layout.class);

		/* format the name of the layout */
		if (newLayout.getName() != null) {
			newLayout.setName(newLayout.getName().replace(".json", "").replaceAll("\"", ""));
		}

		/* format the path of the layout to be an valid URI */
		if (newLayout.getPath() != null) {
			newLayout.setPath(newLayout.getPath().replace((realmCode + "//"), "/").replaceAll("index.json", "")
					.replace(".json", "").replace("sublayouts/", "").replaceAll("//", ""));
		}

		/* download content of the layout */
		newLayout.setData(LayoutUtils.downloadLayoutContent(newLayout));

		/* return the serialized layout */
		return newLayout;
	}
	
	public QCmdMessage sendLayout(String layoutCode, String layoutPath) {
		
		String layout = RulesUtils.getLayout(this.realm, layoutPath);
		QCmdLayoutMessage layoutCmd = new QCmdLayoutMessage(layoutCode, layout);
		return layoutCmd;
	}
	
	public QCmdMessage sendView(LayoutViewData viewData) {
		
		if(viewData == null) {
			return null;
		}
		
		switch(viewData.viewType) {
		case Custom:
			if (viewData.getAdditionalData() != null) {
				
				String sublayoutPath = null;
				String layoutCode = null;
				
				for (Map.Entry<String, Object> entry : viewData.getAdditionalData().entrySet()) {
					if(entry.getKey().equals("sublayoutPath")) {
						sublayoutPath = (String) entry.getValue();
					}
					
					if(entry.getKey().equals("layoutCode")) {
						layoutCode = (String) entry.getValue();
					}
				}
				
				if(sublayoutPath != null && layoutCode != null) {
					String items = RulesUtils.getLayout(this.realm, "/" + sublayoutPath);
					String root = viewData.getRoot() != null ? viewData.getRoot() : "test";
					QCmdSubLayoutMessage sublayoutMessage = new QCmdSubLayoutMessage(layoutCode, items, viewData.getIsPopup(), root);
					
					return sublayoutMessage;
				}

			}
		case SplitView: {
			
			if(viewData.getAdditionalData() != null) {
				
				QCmdViewMessage viewCmd = new QCmdViewMessage(viewData.viewType.getViewType(), viewData.getRoot());
				viewCmd.setIsPopup(viewData.getIsPopup());
				
				List<QCmdViewMessage> viewList = new ArrayList<>();
				
				viewData.getAdditionalData().forEach((key, value) -> {
					
					QCmdViewMessage v = null;
					LayoutViewData vd = null;
					
					if(value instanceof LayoutViewData) {
						
						vd = (LayoutViewData) value;
					
					}else if(value instanceof com.google.gson.internal.LinkedTreeMap) {
						
						/* When setLastLayout is being set for splitView, the value is not of LayoutViewData instance. It is a linkedTreeMap, thus using this check */
						Gson gson = new Gson();
    						JsonElement jsonElement = gson.toJsonTree(value);
    						vd = gson.fromJson(jsonElement, LayoutViewData.class);
    						
					} else {
						System.out.println("split view additional data value is of not LayoutViewData/LinkedTreeMap type");
					}
					
					if(vd != null) {
						if(vd.viewType.getViewType().equals(ViewType.Tab.getViewType())) {
							v = new QCmdTabViewMessage(vd.getDataViewMessageArr(), vd.getTabViewArr());
						} else {
							v = new QCmdViewMessage(vd.viewType.getViewType(), vd.getRoot());
						}
						
						try {
							viewList.add(v);
						}
						catch(Exception e) {
							
						}
					}			
					
				});
				
				if(viewList != null && viewList.size() > 0) {
					QCmdViewMessage[] views = viewList.stream().toArray(QCmdViewMessage[]::new);
					viewCmd.setRoot(views);
					if(viewData.getContextRoot() != null) {						
						viewCmd.setContextRoot(viewData.getContextRoot());
					}
				}
						
				return viewCmd;
			}
		}
		case Table: {
			
			if(viewData.getAdditionalData() != null) {
                
				List<QCmdTableMessage> columns = new ArrayList<>();
				List<QCmdViewMessageAction> actions = new ArrayList<>();
				
				for (Map.Entry<String, Object> entry : viewData.getAdditionalData().entrySet()) {
				    String key = entry.getKey();
				    Object value = entry.getValue();
				    
				    if(key.equals("columns")){
				    		
				    		if(value instanceof java.util.List) {
				    			List<QCmdTableMessage> cmdTableArr = (List<QCmdTableMessage>) value;
					    		
					    		if(cmdTableArr != null && cmdTableArr.size() > 0) {
					    			
					    			/* the array list is not of QCmdTableMessage instance. It is a linkedTreeMap */
					    			for(Object cmdTable : cmdTableArr) {
					    				Gson gson = new Gson();
					    				JsonElement jsonElement = gson.toJsonTree(cmdTable);
					    				QCmdTableMessage msgTable = gson.fromJson(jsonElement, QCmdTableMessage.class);
					    				//QCmdTableMessage msgTable = JsonUtils.fromJson(cmdTable.toString(), QCmdTableMessage.class);
					    				columns.add(msgTable);
					    			}
					    		}
				    		}
				    		
				    		if(value instanceof QCmdTableMessage[]) {
				    			QCmdTableMessage[] cmdTableArr = (QCmdTableMessage[])value;
					    		
					    		if(cmdTableArr != null && cmdTableArr.length > 0) {
					    			
					    			for(QCmdTableMessage cmdTable : cmdTableArr) {
					    				
					    				columns.add(cmdTable);
					    			}
					    		}
				    		}    		

					}
					if(key.equals("actions")){
						//actions.add((QCmdViewMessageAction)value);
						
						/* the array list is not of QCmdViewMessageAction instance. It is a linkedTreeMap */
						if(value instanceof java.util.List) {
							List<QCmdViewMessageAction> cmdActionArr = (List<QCmdViewMessageAction>) value;
							
							if(cmdActionArr != null && cmdActionArr.size() > 0) {
								for (Object cmdAction : cmdActionArr) {
									Gson gson = new Gson();
									JsonElement jsonElement = gson.toJsonTree(cmdAction);
									QCmdViewMessageAction msgAction = gson.fromJson(jsonElement, QCmdViewMessageAction.class);
									// QCmdTableMessage msgTable = JsonUtils.fromJson(cmdTable.toString(),
									// QCmdTableMessage.class);
									actions.add(msgAction);
								}
							}
							
						}
						
						if (value instanceof QCmdViewMessageAction[]) {
							QCmdViewMessageAction[] cmdActionArr = (QCmdViewMessageAction[]) value; 

							if (cmdActionArr != null && cmdActionArr.length > 0) {

								for (QCmdViewMessageAction cmdAction : cmdActionArr) {
									actions.add(cmdAction);
								}
							}
						}					

					}
					
				}
				QCmdTableMessage[] columnsArray = columns.toArray(new QCmdTableMessage[columns.size()]);
				QCmdViewMessageAction[] actionsArray = actions.toArray(new QCmdViewMessageAction[actions.size()]);			
			
				QCmdViewTableMessage tableView = new QCmdViewTableMessage(viewData.getRoot());
				tableView.setContextRoot(viewData.getContextRoot());
				tableView.setColumns(columnsArray);
				tableView.setActions(actionsArray);
				
				return tableView;
				//setLastView(tableView);
				
			}
		}
		
		default: {
			
			QCmdViewMessage viewCmd = new QCmdViewMessage(viewData.viewType.getViewType(), viewData.getRoot());
			viewCmd.setIsPopup(viewData.getIsPopup());
			return viewCmd;
		}
		}
	}
}
