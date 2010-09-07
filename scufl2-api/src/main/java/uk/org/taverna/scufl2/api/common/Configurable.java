package uk.org.taverna.scufl2.api.common;

import java.util.Set;

/**
 * @author Alan R Williams
 *
 */
public interface Configurable extends WorkflowBean {
	
	/**
	 * @return
	 */
	public Set<ConfigurableProperty> getConfigurableProperties();
	
	/**
	 * @param configurableProperties
	 */
	public void setConfigurableProperties(Set<ConfigurableProperty> configurableProperties);
}