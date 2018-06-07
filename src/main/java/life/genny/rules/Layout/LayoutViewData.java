package life.genny.rules.Layout;

import java.util.Map;

public class LayoutViewData {

	ViewType viewType;
	private String root;
	private Boolean isPopup;
	private Map<String, LayoutViewData> additionalData;
	
	public LayoutViewData(ViewType viewType, String root, Boolean isPopup) {
		this.viewType = viewType;
		this.setRoot(root);
		this.isPopup = isPopup;
	}

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}
	
	public Map<String, LayoutViewData> getAdditionalData() {
		return additionalData;
	}

	public void setAdditionalData(Map<String, LayoutViewData> additionalData) {
		this.additionalData = additionalData;
	}
}
