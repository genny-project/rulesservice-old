package life.genny.rules.Layout;

public enum ViewType {
	
	Form("FORM_VIEW"),
	Table("TABLE_VIEW"),
	List("LIST_VIEW"),
	SplitView("SPLIT_VIEW"),
	Custom("SUBLAYOUT");
	
	private String viewType;
	
	ViewType(String viewType) {
		this.viewType = viewType;
	}
	
	public String getViewType() {
		return viewType;
	}
}
