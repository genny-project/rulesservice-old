package life.genny.rules.Layout;

import java.util.Map;

public class LayoutViewData {

	ViewType viewType;
	private String root;
	private String contextRoot;
	private Boolean isPopup;
	private Map<String, Object> additionalData;

	public LayoutViewData(ViewType viewType, String root, Boolean isPopup) {
		this.viewType = viewType;
		this.setRoot(root);
		this.isPopup = isPopup;
	}
	public LayoutViewData(ViewType viewType, String root, Boolean isPopup, String contextRoot) {
		this.viewType = viewType;
		this.setRoot(root);
		this.isPopup = isPopup;
		this.contextRoot = contextRoot;
	}

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}

	public String getContextRoot() {
		return contextRoot;
	}

	public void setContextRoot(String contextRoot) {
		this.contextRoot = contextRoot;
	}

	public Map<String, Object> getAdditionalData() {
		return additionalData;
	}

	public void setAdditionalData(Map<String, Object> additionalData) {
		this.additionalData = additionalData;
	}

	public void setIsPopup(Boolean isPopup) {
		this.isPopup = true;
	}

	public Boolean getIsPopup() {
		return this.isPopup;
	}
}
