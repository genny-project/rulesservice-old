package life.genny.utils.Layout;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import com.google.gson.annotations.Expose;

import life.genny.qwanda.message.QCmdViewMessage;
import life.genny.qwanda.message.QTabView;

public class LayoutViewData implements Serializable{
		
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	@Expose
	ViewType viewType;
	
	@Expose
	private String root;
	
	@Expose
	private String contextRoot;
	
	@Expose
	private Boolean isPopup;
	
	@Expose
	private Map<String, Object> additionalData;
	
	@Expose
	private QCmdViewMessage[] dataViewMessageArr;
	
	@Expose
	private QTabView[] tabViewArr;
	
	/* START :: constructor for sublayouts */
	/**
	 * 
	 * @param viewType for sublayouts is : Custom
	 * @param additionalData
	 * @param root => root != null ? root : "test"
	 * @param isPopup
	 */
	public LayoutViewData(ViewType viewType, Map<String, Object> additionalData, String root, Boolean isPopup) {
		this.viewType = viewType;
		
		/* for sublayouts : additionalData map will have keys : layoutCode and sublayoutPath */
		this.additionalData = additionalData;
		this.root = root;
		this.isPopup = isPopup;
	}
	
	/* END :: constructor for sublayouts */

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
	
	/* For tab view data */
	public LayoutViewData(ViewType viewType, QCmdViewMessage[] dataViewMessageArr, QTabView[] tabViewArr) {
		this.viewType = viewType;
		this.dataViewMessageArr = dataViewMessageArr;
		this.tabViewArr = tabViewArr;
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

	/**
	 * @return the dataViewMessageArr
	 */
	public QCmdViewMessage[] getDataViewMessageArr() {
		return dataViewMessageArr;
	}

	/**
	 * @param dataViewMessageArr the dataViewMessageArr to set
	 */
	public void setDataViewMessageArr(QCmdViewMessage[] dataViewMessageArr) {
		this.dataViewMessageArr = dataViewMessageArr;
	}

	/**
	 * @return the tabViewArr
	 */
	public QTabView[] getTabViewArr() {
		return tabViewArr;
	}

	/**
	 * @param tabViewArr the tabViewArr to set
	 */
	public void setTabViewArr(QTabView[] tabViewArr) {
		this.tabViewArr = tabViewArr;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LayoutViewData [viewType=" + viewType + ", root=" + root + ", contextRoot=" + contextRoot + ", isPopup="
				+ isPopup + ", additionalData=" + additionalData + ", dataViewMessageArr="
				+ Arrays.toString(dataViewMessageArr) + ", tabViewArr=" + Arrays.toString(tabViewArr) + "]";
	}
	
	
}
