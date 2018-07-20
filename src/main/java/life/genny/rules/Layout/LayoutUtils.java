package life.genny.rules.Layout;

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
import life.genny.rules.BaseEntityUtils;
import life.genny.rules.RulesUtils;
import life.genny.utils.VertxUtils;

import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
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

		String pathToLayout = subpath; // channel40 + /sublayouts

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
			return null;
		case SplitView: {
			
			if(viewData.getAdditionalData() != null) {
				
				QCmdViewMessage viewCmd = new QCmdViewMessage(viewData.viewType.getViewType(), viewData.getRoot());
				viewCmd.setIsPopup(viewData.getIsPopup());
				
				JsonArray views = new JsonArray();
				
				viewData.getAdditionalData().forEach((key, value) -> {
					
					LayoutViewData vd = (LayoutViewData) value;
					QCmdViewMessage v = new QCmdViewMessage(vd.viewType.getViewType(), vd.getRoot());
					try {
						views.add(JsonUtils.toJson(v));
					}
					catch(Exception e) {}
				});
				
				viewCmd.setRoot(views);
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
				    		QCmdTableMessage[] cmdTableArr = (QCmdTableMessage[])value;
				    		
				    		if(cmdTableArr != null && cmdTableArr.length >1) {
				    			
				    			for(QCmdTableMessage cmdTable : cmdTableArr) {
				    				
				    				columns.add(cmdTable);
				    			}
				    		}

					}
					if(key.equals("actions")){
						//actions.add((QCmdViewMessageAction)value);
						
						QCmdViewMessageAction[] cmdActionArr = (QCmdViewMessageAction[])value;
			    		
				    		if(cmdActionArr != null && cmdActionArr.length >1) {
				    			
				    			for(QCmdViewMessageAction cmdAction : cmdActionArr) {
				    				
				    				actions.add(cmdAction);
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
