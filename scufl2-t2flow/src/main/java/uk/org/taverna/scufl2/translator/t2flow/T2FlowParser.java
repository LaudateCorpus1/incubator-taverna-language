package uk.org.taverna.scufl2.translator.t2flow;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import uk.org.taverna.scufl2.api.activity.ActivityType;
import uk.org.taverna.scufl2.api.activity.InputActivityPort;
import uk.org.taverna.scufl2.api.activity.OutputActivityPort;
import uk.org.taverna.scufl2.api.bindings.Bindings;
import uk.org.taverna.scufl2.api.bindings.ProcessorBinding;
import uk.org.taverna.scufl2.api.bindings.ProcessorInputPortBinding;
import uk.org.taverna.scufl2.api.bindings.ProcessorOutputPortBinding;
import uk.org.taverna.scufl2.api.common.ConfigurableProperty;
import uk.org.taverna.scufl2.api.common.Named;
import uk.org.taverna.scufl2.api.common.ToBeDecided;
import uk.org.taverna.scufl2.api.configurations.ConfigurablePropertyConfiguration;
import uk.org.taverna.scufl2.api.configurations.Configuration;
import uk.org.taverna.scufl2.api.container.TavernaResearchObject;
import uk.org.taverna.scufl2.api.core.DataLink;
import uk.org.taverna.scufl2.api.core.IterationStrategy;
import uk.org.taverna.scufl2.api.core.Processor;
import uk.org.taverna.scufl2.api.core.Workflow;
import uk.org.taverna.scufl2.api.port.InputProcessorPort;
import uk.org.taverna.scufl2.api.port.InputWorkflowPort;
import uk.org.taverna.scufl2.api.port.OutputProcessorPort;
import uk.org.taverna.scufl2.api.port.OutputWorkflowPort;
import uk.org.taverna.scufl2.api.port.ReceiverPort;
import uk.org.taverna.scufl2.api.port.SenderPort;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Activity;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.AnnotatedGranularDepthPort;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.AnnotatedGranularDepthPorts;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.ConfigBean;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Dataflow;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Datalinks;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.DepthPort;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.DepthPorts;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.DispatchStack;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.GranularDepthPort;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.GranularDepthPorts;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.IterationStrategyStack;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Link;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.LinkType;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Map;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Mapping;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Port;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Ports;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Processors;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Raven;
import uk.org.taverna.scufl2.xml.t2flow.jaxb.Role;

@SuppressWarnings("restriction")
public class T2FlowParser {

	public static <T extends Named> T findNamed(Collection<T> namedObjects,
			String name) {
		for (T named : namedObjects) {
			if (named.getName().equals(name)) {
				return named;
			}
		}
		return null;
	}

	protected ThreadLocal<uk.org.taverna.scufl2.api.activity.Activity> currentActivity = new ThreadLocal<uk.org.taverna.scufl2.api.activity.Activity>();
	protected ThreadLocal<Bindings> currentBindings = new ThreadLocal<Bindings>();

	protected ThreadLocal<Processor> currentProcessor = new ThreadLocal<Processor>();

	protected ThreadLocal<ProcessorBinding> currentProcessorBinding = new ThreadLocal<ProcessorBinding>();

	// Currently parsing
	protected ThreadLocal<TavernaResearchObject> currentResearchObject = new ThreadLocal<TavernaResearchObject>();

	protected ThreadLocal<Workflow> currentWorkflow = new ThreadLocal<Workflow>();

	private final JAXBContext jc;
	private final Logger logger = Logger.getLogger(T2FlowParser.class);
	private boolean strict = true;
	private final ThreadLocal<Unmarshaller> unmarshaller;

	public T2FlowParser() throws JAXBException {
		jc = JAXBContext.newInstance("uk.org.taverna.scufl2.xml.t2flow.jaxb",
				getClass().getClassLoader());
		unmarshaller = new ThreadLocal<Unmarshaller>() {
			@Override
			protected Unmarshaller initialValue() {
				try {
					return jc.createUnmarshaller();
				} catch (JAXBException e) {
					logger.error("Could not create unmarshaller", e);
					return null;
				}
			}
		};
	}

	protected ReceiverPort findReceiverPort(Workflow wf, Link sink)
			throws ParseException {
		if (sink.getType().equals(LinkType.DATAFLOW)) {
			String portName = sink.getPort();
			OutputWorkflowPort candidate = wf.getOutputPorts().getByName(
					portName);
			if (candidate == null) {
				throw new ParseException("Link to unknown workflow port "
						+ portName);
			}
			return candidate;
		} else if (sink.getType().equals(LinkType.PROCESSOR)) {
			String processorName = sink.getProcessor();
			Processor processor = wf.getProcessors().getByName(processorName);
			if (processor == null) {
				throw new ParseException("Link to unknown processor "
						+ processorName);
			}
			String portName = sink.getPort();
			InputProcessorPort candidate = processor.getInputPorts().getByName(
					portName);
			if (candidate == null) {
				throw new ParseException("Link to unknown port " + portName
						+ " in " + processorName);
			}
			return candidate;
		} else if (sink.getType().equals(LinkType.MERGE)) {
			throw new ParseException(
					"Translation of merges not yet implemented");
		}
		throw new ParseException("Could not parse receiver " + sink);
	}

	protected SenderPort findSenderPort(Workflow wf, Link source)
			throws ParseException {
		if (source.getType().equals(LinkType.DATAFLOW)) {
			String portName = source.getPort();
			InputWorkflowPort candidate = wf.getInputPorts()
					.getByName(portName);
			if (candidate == null) {
				throw new ParseException("Link from unknown workflow port "
						+ portName);
			}
			return candidate;
		} else if (source.getType().equals(LinkType.PROCESSOR)) {
			String processorName = source.getProcessor();
			Processor processor = wf.getProcessors().getByName(processorName);
			if (processor == null) {
				throw new ParseException("Link from unknown processor "
						+ processorName);
			}
			String portName = source.getPort();
			OutputProcessorPort candidate = processor.getOutputPorts()
					.getByName(portName);
			if (candidate == null) {
				throw new ParseException("Link from unknown port " + portName
						+ " in " + processorName);
			}
			return candidate;
		} else if (source.getType().equals(LinkType.MERGE)) {
			throw new ParseException(
					"Translation of merges not yet implemented");
		}
		throw new ParseException("Could not parse sender " + source);
	}

	public boolean isStrict() {
		return strict;
	}

	protected void makeDefaultBindings(
			uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow wf) {
		Bindings bindings = new Bindings(wf.getProducedBy());
		currentResearchObject.get().getBindings().add(bindings);
		currentBindings.set(bindings);
	}

	protected URI mapActivityFromRaven(Raven raven, String activityClass) {
		URI ravenURI = URI
				.create("http://ns.taverna.org.uk/2010/activity/raven/");
		// TODO: Perform actual mapping
		return ravenURI.resolve(raven.getGroup() + "/" + raven.getArtifact()
				+ "/" + raven.getVersion() + "/" + activityClass);
	}

	protected uk.org.taverna.scufl2.api.activity.Activity parseActivity(
			Activity origActivity) {
		Raven raven = origActivity.getRaven();
		String activityClass = origActivity.getClazz();
		URI activityId = mapActivityFromRaven(raven, activityClass);
		uk.org.taverna.scufl2.api.activity.Activity newActivity = new uk.org.taverna.scufl2.api.activity.Activity();
		newActivity.setType(new ActivityType(activityId.toASCIIString()));
		return newActivity;
	}

	protected void parseActivityBinding(Activity origActivity)
			throws ParseException {
		ProcessorBinding processorBinding = new ProcessorBinding();
		currentBindings.get().getProcessorBindings().add(processorBinding);
		processorBinding.setBoundProcessor(currentProcessor.get());
		currentProcessorBinding.set(processorBinding);

		uk.org.taverna.scufl2.api.activity.Activity newActivity = parseActivity(origActivity);
		currentActivity.set(newActivity);
		currentResearchObject.get().getActivities().add(newActivity);
		processorBinding.setBoundActivity(newActivity);

		parseActivityInputMap(origActivity.getInputMap());
		parseActivityOutputMap(origActivity.getOutputMap());
		parseActivityConfiguration(origActivity.getConfigBean());

		currentActivity.remove();
		currentProcessorBinding.remove();
	}

	protected void parseActivityConfiguration(ConfigBean configBean) {
		Configuration configuration = new Configuration();
		configuration.setConfigured(currentActivity.get());

		Object config = configBean.getAny();
		if (config instanceof Element) {

			unmarshaller.get().unmarshal(config);

			ConfigurablePropertyConfiguration configurablePropertyConfiguration = new ConfigurablePropertyConfiguration();
			configurablePropertyConfiguration.setParent(configuration);
			ConfigurableProperty configuredProperty = new ConfigurableProperty(
					"xml");
			configurablePropertyConfiguration
					.setConfiguredProperty(configuredProperty);
			configurablePropertyConfiguration.setValue(config);
		} else {

			System.out.println("Checking " + config + " " + config.getClass());
			BeanInfo configBeanInfo;
			try {
				configBeanInfo = Introspector.getBeanInfo(config.getClass());
			} catch (IntrospectionException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return;
			}
			for (PropertyDescriptor property : configBeanInfo
					.getPropertyDescriptors()) {
				if (property.getReadMethod() == null) {
					continue;
				}
				ConfigurablePropertyConfiguration configurablePropertyConfiguration = new ConfigurablePropertyConfiguration();
				configurablePropertyConfiguration.setParent(configuration);

				String propertyName = property.getName();
				ConfigurableProperty configuredProperty = new ConfigurableProperty(
						propertyName);
				configurablePropertyConfiguration
						.setConfiguredProperty(configuredProperty);
				try {
					Object value = property.getReadMethod().invoke(config);
					System.out.println(propertyName + ": " + value);
					if (value instanceof Document) {
						Document document = (Document) value;
						value = document.getDocumentElement();
					}
					configurablePropertyConfiguration.setValue(value);
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		currentResearchObject.get().getConfigurations().add(configuration);

	}

	protected void parseActivityInputMap(Map inputMap) throws ParseException {
		for (Mapping mapping : inputMap.getMap()) {
			String fromProcessorOutput = mapping.getFrom();
			String toActivityOutput = mapping.getTo();
			ProcessorInputPortBinding processorInputPortBinding = new ProcessorInputPortBinding();

			InputProcessorPort inputProcessorPort = findNamed(currentProcessor
					.get().getInputPorts(), fromProcessorOutput);
			if (inputProcessorPort == null) {
				String message = "Invalid input port binding, "
						+ "unknown processor port: " + fromProcessorOutput
						+ "->" + toActivityOutput + " in "
						+ currentProcessor.get();
				if (isStrict()) {
					throw new ParseException(message);
				} else {
					logger.warn(message);
					continue;
				}
			}

			InputActivityPort inputActivityPort = new InputActivityPort();
			inputActivityPort.setName(toActivityOutput);
			inputActivityPort.setParent(currentActivity.get());
			currentActivity.get().getInputPorts().add(inputActivityPort);

			processorInputPortBinding.setBoundActivityPort(inputActivityPort);
			processorInputPortBinding.setBoundProcessorPort(inputProcessorPort);
			currentProcessorBinding.get().getInputPortBindings().add(
					processorInputPortBinding);
		}

	}

	protected void parseActivityOutputMap(Map outputMap) throws ParseException {
		for (Mapping mapping : outputMap.getMap()) {
			String fromActivityOutput = mapping.getFrom();
			String toProcessorOutput = mapping.getTo();
			ProcessorOutputPortBinding processorOutputPortBinding = new ProcessorOutputPortBinding();

			OutputProcessorPort outputProcessorPort = findNamed(
					currentProcessor.get().getOutputPorts(), toProcessorOutput);
			if (outputProcessorPort == null) {
				String message = "Invalid output port binding, "
						+ "unknown processor port: " + fromActivityOutput
						+ "->" + toProcessorOutput + " in "
						+ currentProcessor.get();
				if (isStrict()) {
					throw new ParseException(message);
				} else {
					logger.warn(message);
					continue;
				}
			}

			OutputActivityPort outputActivityPort = new OutputActivityPort();
			outputActivityPort.setName(fromActivityOutput);
			outputActivityPort.setParent(currentActivity.get());
			currentActivity.get().getOutputPorts().add(outputActivityPort);

			processorOutputPortBinding.setBoundActivityPort(outputActivityPort);
			processorOutputPortBinding
					.setBoundProcessorPort(outputProcessorPort);
			currentProcessorBinding.get().getOutputPortBindings().add(
					processorOutputPortBinding);
		}

	}

	protected Workflow parseDataflow(Dataflow df) throws ParseException {
		Workflow wf = new Workflow();
		currentWorkflow.set(wf);
		wf.setName(df.getName());
		// wf.setId(df.getId());
		wf.setInputPorts(parseInputPorts(df.getInputPorts()));
		wf.setOutputPorts(parseOutputPorts(df.getOutputPorts()));
		wf.setProcessors(parseProcessors(df.getProcessors()));
		wf.setDatalinks(parseDatalinks(df.getDatalinks()));
		// TODO: Start conditions, annotations
		currentWorkflow.remove();
		return wf;
	}

	protected Set<DataLink> parseDatalinks(Datalinks origLinks)
			throws ParseException {
		HashSet<DataLink> newLinks = new HashSet<DataLink>();
		for (uk.org.taverna.scufl2.xml.t2flow.jaxb.DataLink origLink : origLinks
				.getDatalink()) {
			try {
				SenderPort senderPort = findSenderPort(currentWorkflow.get(),
						origLink.getSource());
				ReceiverPort receiverPort = findReceiverPort(currentWorkflow
						.get(), origLink.getSink());
				DataLink newLink = new DataLink(senderPort, receiverPort);
				newLinks.add(newLink);
			} catch (ParseException ex) {
				logger.warn("Could not translate link:\n" + origLink, ex);
				if (isStrict()) {
					throw ex;
				}
				continue;
			}
		}
		return newLinks;
	}

	protected ToBeDecided parseDispatchStack(DispatchStack dispatchStack) {
		return new ToBeDecided();
	}

	@SuppressWarnings("boxing")
	protected Set<InputWorkflowPort> parseInputPorts(
			AnnotatedGranularDepthPorts originalPorts) throws ParseException {
		Set<InputWorkflowPort> createdPorts = new HashSet<InputWorkflowPort>();
		for (AnnotatedGranularDepthPort originalPort : originalPorts.getPort()) {
			InputWorkflowPort newPort = new InputWorkflowPort(currentWorkflow
					.get(), originalPort.getName());
			newPort.setDepth(originalPort.getDepth().intValue());
			if (!originalPort.getGranularDepth()
					.equals(originalPort.getDepth())) {
				String message = "Specific input port granular depth not "
						+ "supported in scufl2, port " + originalPort.getName()
						+ " has depth " + originalPort.getDepth()
						+ " and granular depth "
						+ originalPort.getGranularDepth();
				logger.warn(message);
				if (isStrict()) {
					throw new ParseException(message);
				}
			}
			createdPorts.add(newPort);
		}
		return createdPorts;
	}

	protected List<IterationStrategy> parseIterationStrategyStack(
			IterationStrategyStack originalStack) {
		List<IterationStrategy> newStack = new ArrayList<IterationStrategy>();
		// TODO: Copy iteration strategy
		return newStack;
	}

	protected Set<OutputWorkflowPort> parseOutputPorts(Ports originalPorts) {
		Set<OutputWorkflowPort> createdPorts = new HashSet<OutputWorkflowPort>();
		for (Port originalPort : originalPorts.getPort()) {
			OutputWorkflowPort newPort = new OutputWorkflowPort(currentWorkflow
					.get(), originalPort.getName());
			createdPorts.add(newPort);
		}
		return createdPorts;

	}

	@SuppressWarnings("boxing")
	protected Set<InputProcessorPort> parseProcessorInputPorts(
			Processor newProc, DepthPorts origPorts) {
		Set<InputProcessorPort> newPorts = new HashSet<InputProcessorPort>();
		for (DepthPort origPort : origPorts.getPort()) {
			InputProcessorPort newPort = new InputProcessorPort(newProc,
					origPort.getName());
			newPort.setDepth(origPort.getDepth().intValue());
			// TODO: What about InputProcessorPort granular depth?
			newPorts.add(newPort);
		}
		return newPorts;
	}

	@SuppressWarnings("boxing")
	protected Set<OutputProcessorPort> parseProcessorOutputPorts(
			Processor newProc, GranularDepthPorts origPorts) {
		Set<OutputProcessorPort> newPorts = new HashSet<OutputProcessorPort>();
		for (GranularDepthPort origPort : origPorts.getPort()) {
			OutputProcessorPort newPort = new OutputProcessorPort(newProc,
					origPort.getName());
			newPort.setDepth(origPort.getDepth().intValue());
			newPort.setGranularDepth(origPort.getGranularDepth().intValue());
			newPorts.add(newPort);
		}
		return newPorts;
	}

	protected Set<Processor> parseProcessors(Processors originalProcessors)
			throws ParseException {
		HashSet<Processor> newProcessors = new HashSet<Processor>();
		for (uk.org.taverna.scufl2.xml.t2flow.jaxb.Processor origProc : originalProcessors
				.getProcessor()) {
			Processor newProc = new Processor(currentWorkflow.get(), origProc
					.getName());
			currentProcessor.set(newProc);
			newProc.setInputPorts(parseProcessorInputPorts(newProc, origProc
					.getInputPorts()));
			newProc.setOutputPorts(parseProcessorOutputPorts(newProc, origProc
					.getOutputPorts()));
			newProc.setDispatchStack(parseDispatchStack(origProc
					.getDispatchStack()));
			newProc
					.setIterationStrategyStack(parseIterationStrategyStack(origProc
							.getIterationStrategyStack()));
			newProcessors.add(newProc);
			for (Activity origActivity : origProc.getActivities().getActivity()) {
				parseActivityBinding(origActivity);
			}
		}
		currentProcessor.remove();
		return newProcessors;
	}

	@SuppressWarnings("unchecked")
	public TavernaResearchObject parseT2Flow(File t2File) throws IOException,
			ParseException, JAXBException {
		JAXBElement<uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow> root = (JAXBElement<uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow>) unmarshaller
				.get()
				.unmarshal(t2File);
		return parseT2Flow(root.getValue());
	}

	@SuppressWarnings("unchecked")
	public TavernaResearchObject parseT2Flow(InputStream t2File)
			throws IOException, JAXBException, ParseException {
		JAXBElement<uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow> root = (JAXBElement<uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow>) unmarshaller
				get().unmarshal(t2File);
		return parseT2Flow(root.getValue());
	}

	public TavernaResearchObject parseT2Flow(
			uk.org.taverna.scufl2.xml.t2flow.jaxb.Workflow wf)
			throws ParseException {

		TavernaResearchObject ro = new TavernaResearchObject();
		currentResearchObject.set(ro);
		makeDefaultBindings(wf);

		for (Dataflow df : wf.getDataflow()) {
			Workflow workflow = parseDataflow(df);
			if (df.getRole().equals(Role.TOP)) {
				ro.setMainWorkflow(workflow);
			}
			ro.getWorkflows().add(workflow);
		}
		if (isStrict() && ro.getMainWorkflow() == null) {
			throw new ParseException("No main workflow");
		}
		currentResearchObject.remove();
		return ro;
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

}