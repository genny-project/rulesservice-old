package life.genny.utils.Layout;

public enum ViewType {
	
	Form("FORM_VIEW"),
	Table("TABLE_VIEW"),
	List("LIST_VIEW"),
	SplitView("SPLIT_VIEW"),
	Custom("SUBLAYOUT"),
	Bucket("BUCKET_VIEW"),
	Detail("DETAIL_VIEW"),
	Tab("TAB_VIEW");
	
	private String viewType;
	
	ViewType(String viewType) {
		this.viewType = viewType;
	}
	
	public String getViewType() {
		return viewType;
	}

}
