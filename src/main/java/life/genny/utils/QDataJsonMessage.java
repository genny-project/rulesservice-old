package life.genny.utils;

import com.google.gson.annotations.Expose;

import io.vertx.core.json.JsonArray;
import life.genny.qwanda.message.QDataMessage;


public class QDataJsonMessage extends QDataMessage {
  private static final long serialVersionUID = 1L;
  @Expose
  private JsonArray items;


  public QDataJsonMessage(final String dataType,final JsonArray items) {
	    this(dataType, items,null);
	  }

  public QDataJsonMessage(final String dataType,final JsonArray items, final String token) {
    super(dataType);
    setItems(items);
    setToken(token);
  }


/**
 * @return the items
 */
public JsonArray getItems() {
	return items;
}


/**
 * @param items the items to set
 */
public void setItems(JsonArray items) {
	this.items = items;
}

 



}
