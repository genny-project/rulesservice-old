package life.genny.facts;

public class User {
	private String uname;
	private String fullName;
	private String realm;
	private String roles;
    private Boolean isAvailable;
    private Boolean isProfileCompleted;
    
	public User(String uname, String fullName, String realm, String roles) {
		this.uname = uname;
		this.fullName = fullName;
		this.realm = realm;
		this.roles = roles;
	}

	public String getUname() {
		return uname;
	}

	public void setUname(String uname) {
		this.uname = uname;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getRoles() {
		return roles;
	}

	public void setRoles(String roles) {
		this.roles = roles;
	}

	public Boolean getIsAvailable() {
		return isAvailable;
	}

	public void setIsAvailable(Boolean isAvailable) {
		this.isAvailable = isAvailable;
	}

	public Boolean getIsProfileCompleted() {
		return isProfileCompleted;
	}

	public void setIsProfileCompleted(Boolean isProfileCompleted) {
		this.isProfileCompleted = isProfileCompleted;
	}

	@Override
	public String toString() {
		return "User [uname=" + uname + ", fullName=" + fullName + ", realm=" + realm + ", roles=" + roles
				+ ", isAvailable=" + isAvailable + ", isProfileCompleted=" + isProfileCompleted + "]";
	}
	

   
	

}
