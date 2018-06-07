package life.genny.rules.Layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import life.genny.qwanda.Layout;
import life.genny.qwanda.entity.BaseEntity;
import life.genny.qwanda.message.QCmdLayoutMessage;
import life.genny.qwanda.message.QCmdMessage;
import life.genny.qwanda.message.QCmdViewFormMessage;
import life.genny.qwandautils.QwandaUtils;
import life.genny.rules.BaseEntityUtils;
import life.genny.rules.RulesUtils;

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

		return layouts.stream().map(layout -> this.baseEntityUtils.baseEntityForLayout(realmCode, token, layout)).collect(Collectors.toList());
	}

	public List<Layout> processLayouts(final String realmCode) {
		return processLayouts(realmCode, null);
	}

	public List<Layout> processLayouts(final String realmCode, String subpath) {

		List<Layout> layouts = new ArrayList<Layout>();

		if(subpath == null) subpath = "";

		String pathToLayout = subpath; //channel40 + /sublayouts

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

						if(sublayoutData != null && sublayoutData.containsKey("path")) {

							/* we get the download_url of the current file */
							String download_url = sublayoutData.getString("path");
							String file_path = download_url.replace(realmCode + "/", "");

							/* if we have found a file we serialize it */
							if(file_path.endsWith(".json")) {
								layouts.add(this.serializeLayout(realmCode, sublayoutData));
							}
							else {

								/* if we have found a folder we recursively download the data inside of it */
                System.out.println("Found subfolder: " + file_path);
								layouts.addAll(this.processLayouts(realmCode, file_path));
							}
						}
					}
				}
			}
			catch(Exception e) {

			}
		}

		return layouts;
	}

	public static String downloadLayoutContent(Layout layout) {

		String content = null;

		if(layout.getDownloadUrl() != null) {

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
		if(newLayout.getName() != null) {
			newLayout.setName(newLayout.getName().replace(".json", "").replaceAll("\"", ""));
		}

		/* format the path of the layout to be an valid URI */
 		if(newLayout.getPath() != null) {
 			newLayout.setPath(newLayout.getPath().replace(realmCode + "/", "").replaceAll("index.json", "").replace(".json", "").replace("sublayouts/", ""));
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
	
	public QCmdMessage sendView(ViewType viewType, String rootCode) {
		return this.sendView(viewType, rootCode, false);
	}
	
	public QCmdMessage sendView(ViewType viewType, String rootCode, Boolean isPopup) {
		
		switch(viewType) {
		case Form: {
			
			QCmdViewFormMessage formViewMessage = new QCmdViewFormMessage(rootCode);
			formViewMessage.setIsPopup(isPopup);
			return formViewMessage;
		}
		default:
			return null;
		}
	}
}
