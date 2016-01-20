package org.jboss.windup.rules.apps.xml.condition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFunction;
import javax.xml.xpath.XPathFunctionException;
import javax.xml.xpath.XPathFunctionResolver;

import org.jboss.forge.furnace.util.Assert;
import org.jboss.windup.config.GraphRewrite;
import org.jboss.windup.config.Variables;
import org.jboss.windup.config.condition.EvaluationStrategy;
import org.jboss.windup.config.parameters.FrameContext;
import org.jboss.windup.config.parameters.FrameCreationContext;
import org.jboss.windup.config.parameters.ParameterizedGraphCondition;
import org.jboss.windup.graph.GraphContext;
import org.jboss.windup.graph.model.WindupVertexFrame;
import org.jboss.windup.graph.service.GraphService;
import org.jboss.windup.rules.apps.xml.model.DoctypeMetaModel;
import org.jboss.windup.rules.apps.xml.model.NamespaceMetaModel;
import org.jboss.windup.rules.apps.xml.model.XmlFileModel;
import org.jboss.windup.rules.apps.xml.model.XmlTypeReferenceModel;
import org.jboss.windup.rules.apps.xml.service.XmlFileService;
import org.jboss.windup.rules.files.model.FileReferenceModel;
import org.jboss.windup.util.ExecutionStatistics;
import org.jboss.windup.util.Logging;
import org.jboss.windup.util.exception.WindupException;
import org.jboss.windup.util.xml.LocationAwareContentHandler;
import org.jboss.windup.util.xml.NamespaceMapContext;
import org.jboss.windup.util.xml.XmlUtil;
import org.ocpsoft.rewrite.config.Condition;
import org.ocpsoft.rewrite.config.ConditionBuilder;
import org.ocpsoft.rewrite.context.EvaluationContext;
import org.ocpsoft.rewrite.param.DefaultParameterStore;
import org.ocpsoft.rewrite.param.DefaultParameterValueStore;
import org.ocpsoft.rewrite.param.Parameter;
import org.ocpsoft.rewrite.param.ParameterStore;
import org.ocpsoft.rewrite.param.ParameterValueStore;
import org.ocpsoft.rewrite.param.RegexParameterizedPatternParser;
import org.ocpsoft.rewrite.util.Maps;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Handles matching on {@link XmlFileModel} objects and creating {@link XmlTypeReferenceModel} objects on the matching nodes.
 */
public class XmlFile extends ParameterizedGraphCondition implements XmlFileDTD, XmlFileIn, XmlFileNamespace, XmlFileResult, XmlFileXpath
{
    private static final Logger LOG = Logging.get(XmlFile.class);

    protected static final String WINDUP_NS_PREFIX = "windup";
    protected static final String WINDUP_NS_URI = "http://windup.jboss.org/windupv2functions";

    private static XPathFactory factory = XPathFactory.newInstance();
    private final XmlFileFunctionResolver xmlFileFunctionResolver;
    private final XPath xpathEngine;

    private String xpathString;
    private XPathExpression compiledXPath;
    private Map<String, String> namespaces = new HashMap<>();
    private String publicId;
    private String xpathResultMatch;
    private String dtdNamespace;

    // just to extract required parameter names
    private RegexParameterizedPatternParser xpathPattern;

    private RegexParameterizedPatternParser fileNamePattern;

    public void setXpathResultMatch(String xpathResultMatch)
    {
        this.xpathResultMatch = xpathResultMatch;
    }

    private XmlFile(String xpath)
    {
        this();
        setXpath(xpath);
    }

    XmlFile()
    {
        this.namespaces.put(WINDUP_NS_PREFIX, WINDUP_NS_URI);

        this.xpathEngine = factory.newXPath();
        final XPathFunctionResolver originalResolver = this.xpathEngine.getXPathFunctionResolver();
        xmlFileFunctionResolver = new XmlFileFunctionResolver(originalResolver);
        this.xpathEngine.setXPathFunctionResolver(xmlFileFunctionResolver);
    }

    public String getXpathString()
    {
        return xpathString;
    }

    /**
     * Create a new {@link XmlFile} {@link Condition}.
     */
    public static XmlFileXpath matchesXpath(String xpath)
    {
        return new XmlFile(xpath);
    }

    public XmlFileDTD andDTDPublicId(String publicIdRegex)
    {
        this.publicId = publicIdRegex;
        return this;
    }

    /**
     * Create a new {@link XmlFile} that matches on the provided DTD namespace.
     */
    public static XmlFileDTD withDTDSystemId(String dtdNamespace)
    {
        XmlFile xmlFile = new XmlFile();
        xmlFile.dtdNamespace = dtdNamespace;
        return xmlFile;
    }

    public XmlFileDTD andDTDSystemId(String dtdNamespace)
    {
        this.dtdNamespace = dtdNamespace;
        return this;
    }

    /**
     * Output the results using the provided variable name.
     */
    public ConditionBuilder as(String variable)
    {
        Assert.notNull(variable, "Variable name must not be null.");
        this.setOutputVariablesName(variable);
        return this;
    }

    /**
     * Scan only files that match the given file name.
     */
    public XmlFileIn inFile(String fileName)
    {
        this.fileNamePattern = new RegexParameterizedPatternParser(fileName);
        return this;
    }

    /**
     * Only return results that match the given regex.
     */
    public XmlFileResult resultMatches(String regex)
    {
        this.xpathResultMatch = regex;
        return this;
    }

    public XPathExpression getXPathExpression()
    {
        return compiledXPath;
    }

    public RegexParameterizedPatternParser getInFilePattern()
    {
        return fileNamePattern;
    }

    public String getPublicId()
    {
        return publicId;
    }

    /**
     * Specify the name of the variables to base this query on.
     *
     * @param fromVariable
     * @return
     */
    public static XmlFileFrom from(String fromVariable)
    {
        return new XmlFileFrom(fromVariable);
    }

    @Override
    public void setParameterStore(ParameterStore store)
    {
        if (this.xpathPattern != null)
        {
            this.xpathPattern.setParameterStore(store);
        }
    }

    @Override
    public Set<String> getRequiredParameterNames()
    {
        Set<String> result = new HashSet<>();
        if (this.xpathPattern != null)
        {
            result.addAll(xpathPattern.getRequiredParameterNames());
        }
        if (this.fileNamePattern != null)
        {
            result.addAll(fileNamePattern.getRequiredParameterNames());
        }
        return result;
    }

    @Override
    protected String getVarname()
    {
        return getOutputVariablesName();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean evaluateAndPopulateValueStores(final GraphRewrite event, final EvaluationContext context,
                final FrameCreationContext frameCreationContext)
    {
        return evaluate(event, context, new XmlFileEvaluationStrategy()
        {
            private LinkedHashMap<String, List<WindupVertexFrame>> variables;

            @Override
            public void modelMatched()
            {
                this.variables = new LinkedHashMap<>();
                frameCreationContext.beginNew((Map) this.variables);
            }

            @Override
            @SuppressWarnings("rawtypes")
            public void modelSubmitted(WindupVertexFrame model)
            {
                Maps.addListValue(this.variables, getVarname(), model);
            }

            @Override
            public boolean submitValue(Parameter<?> parameter, String value)
            {
                ParameterValueStore valueStore = DefaultParameterValueStore.getInstance(context);
                return valueStore.submit(event, context, parameter, value);
            }

            @Override
            public void modelSubmissionRejected()
            {
                if (variables != null)
                {
                    this.variables = null;
                    frameCreationContext.rollback();
                }
            }
        });
    }

    private interface XmlFileEvaluationStrategy extends EvaluationStrategy
    {
        public boolean submitValue(Parameter<?> parameter, String value);
    }

    @Override
    protected boolean evaluateWithValueStore(final GraphRewrite event, final EvaluationContext context, final FrameContext frameContext)
    {
        boolean result = evaluate(event, context, new XmlFileEvaluationStrategy()
        {
            @Override
            public void modelMatched()
            {
            }

            @Override
            public void modelSubmitted(WindupVertexFrame model)
            {
            }

            @Override
            public boolean submitValue(Parameter<?> parameter, String value)
            {
                ParameterValueStore valueStore = DefaultParameterValueStore.getInstance(context);
                String existingValue = valueStore.retrieve(parameter);
                if (existingValue == null)
                {
                    return valueStore.submit(event, context, parameter, value);
                }
                else
                {
                    return valueStore.isValid(event, context, parameter, value);
                }
            }

            @Override
            public void modelSubmissionRejected()
            {
            }
        });

        if (result == false)
            frameContext.reject();

        return result;
    }

    private boolean evaluate(final GraphRewrite event, final EvaluationContext context, final XmlFileEvaluationStrategy evaluationStrategy)
    {
        try
        {
            ExecutionStatistics.get().begin("XmlFile.evaluate");
            // list will cache all the created xpath matches for this given condition running
            final List<WindupVertexFrame> results = new ArrayList<>();
            final GraphContext graphContext = event.getGraphContext();
            GraphService<XmlFileModel> xmlResourceService = new GraphService<>(graphContext, XmlFileModel.class);
            Iterable<? extends WindupVertexFrame> allXmls;
            if (getInputVariablesName() == null || getInputVariablesName().isEmpty())
            {
                allXmls = xmlResourceService.findAll();
            }
            else
            {
                allXmls = Variables.instance(event).findVariable(getInputVariablesName());
            }
            Set<String> xmlCache = new HashSet<>();
            for (WindupVertexFrame iterated : allXmls)
            {
                final XmlFileModel xml;
                if (iterated instanceof FileReferenceModel)
                {
                    xml = (XmlFileModel) ((FileReferenceModel) iterated).getFile();
                }
                else if (iterated instanceof XmlFileModel)
                {
                    xml = (XmlFileModel) iterated;
                }
                else
                {
                    throw new WindupException("XmlFile was called on the wrong graph type ( " + iterated.toPrettyString()
                                + ")");
                }

                // in case of taking a result of other XmlFile condition as input, multiple FileReferenceModels may reference the same XmlFileModel.
                if (xmlCache.contains(xml.getFilePath()))
                {
                    continue;
                }
                else
                {
                    xmlCache.add(xml.getFilePath());
                }

                if (fileNamePattern != null)
                {
                    final ParameterStore store = DefaultParameterStore.getInstance(context);
                    Pattern compiledPattern = fileNamePattern.getCompiledPattern(store);
                    String pattern = compiledPattern.pattern();
                    String fileName = xml.getFileName();
                    if (!fileName.matches(pattern))
                    {
                        continue;
                    }
                }
                if (( publicId != null && !publicId.isEmpty() ) || dtdNamespace!=null)
                {
                    DoctypeMetaModel doctype = xml.getDoctype();
                    if (doctype == null )
                    {
                        continue;
                    }
                    if(publicId != null && ( (doctype.getPublicId() == null) || !doctype.getPublicId().matches(publicId))) {
                        continue;
                    }
                    if(dtdNamespace != null && ( (doctype.getSystemId() == null) || !doctype.getSystemId().matches(dtdNamespace))) {
                        continue;
                    }


                    if (xpathString == null)
                    {
                        evaluationStrategy.modelMatched();
                        // if the xpath is not set and therefore we have the result already
                        if (fileNamePattern != null)
                        {
                            if (!fileNamePattern.parse(xml.getFileName()).submit(event, context))
                            {
                                evaluationStrategy.modelSubmissionRejected();
                                continue;
                            }
                        }
                        evaluationStrategy.modelSubmitted(xml);
                        results.add(xml);
                    }

                }
                if (xpathString != null)
                {
                    String xpathStringWithParameterFunctions = XmlFileXPathTransformer.transformXPath(this.xpathString);
                    LOG.fine("XmlFile compiled: " + this.xpathString + " to " + xpathStringWithParameterFunctions);

                    XmlFileService xmlFileService = new XmlFileService(graphContext);
                    Document document = xmlFileService.loadDocumentQuiet(context, xml);
                    if (document != null)
                    {
                        final ParameterStore store = DefaultParameterStore.getInstance(context);

                        final XmlFileParameterMatchCache paramMatchCache = new XmlFileParameterMatchCache();
                        this.xmlFileFunctionResolver.registerFunction(WINDUP_NS_URI, "startFrame",
                                    new XmlFileStartFrameXPathFunction(paramMatchCache));
                        this.xmlFileFunctionResolver
                                    .registerFunction(WINDUP_NS_URI, "evaluate", new XmlFileEvaluateXPathFunction(evaluationStrategy));
                        this.xmlFileFunctionResolver.registerFunction(WINDUP_NS_URI, "matches", new XmlFileMatchesXPathFunction(context, store,
                                    paramMatchCache, event));
                        this.xmlFileFunctionResolver.registerFunction(WINDUP_NS_URI, "persist", new XmlFilePersistXPathFunction(event, context, xml,
                                    evaluationStrategy, store, paramMatchCache, results));

                        if (compiledXPath == null)
                        {
                            NamespaceMapContext nsContext = new NamespaceMapContext(namespaces);
                            this.xpathEngine.setNamespaceContext(nsContext);
                            try
                            {
                                this.compiledXPath = xpathEngine.compile(xpathStringWithParameterFunctions);
                            }
                            catch (Exception e)
                            {
                                String message = e.getMessage();

                                // brutal hack to try to get a reasonable error message (ugly, but it seems to work)
                                if (message == null && e.getCause() != null && e.getCause().getMessage() != null)
                                {
                                    message = e.getCause().getMessage();
                                }
                                LOG.severe("Condition: " + this + " failed to run, as the following xpath was uncompilable: " + xpathString
                                            + " (compiled contents: " + xpathStringWithParameterFunctions + ") due to: "
                                            + message);
                                return false;
                            }
                        }

                        /**
                         * This actually does the work.
                         */
                        XmlUtil.xpathNodeList(document, compiledXPath);
                        evaluationStrategy.modelSubmissionRejected();
                    }
                }
            }
            setResults(event, getOutputVariablesName(), results);

            return !results.isEmpty();
        }
        finally
        {
            ExecutionStatistics.get().end("XmlFile.evaluate");
        }
    }

    public XmlFileNamespace namespace(String prefix, String url)
    {
        namespaces.put(prefix, url);
        return this;
    }

    public void setXpath(String xpath)
    {
        this.xpathString = xpath;
        this.compiledXPath = null;

        if (xpath != null)
        {
            this.xpathPattern = new RegexParameterizedPatternParser(this.xpathString);
        }
    }

    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("XmlFile");
        if (getInputVariablesName() != null)
        {
            builder.append(".inputVariable(" + getInputVariablesName() + ")");
        }
        if (xpathString != null)
        {
            builder.append(".matches(" + xpathString + ")");
        }
        if (fileNamePattern != null)
        {
            builder.append(".inFile(" + fileNamePattern.toString() + ")");
        }
        if (publicId != null)
        {
            builder.append(".withDTDPublicId(" + publicId + ")");
        }
        builder.append(".as(" + getOutputVariablesName() + ")");
        return builder.toString();
    }

    final class XmlFilePersistXPathFunction implements XPathFunction
    {
        private final GraphRewrite event;
        private final EvaluationContext context;
        private final XmlFileModel xml;
        private final XmlFileEvaluationStrategy evaluationStrategy;
        private final ParameterStore store;
        private final XmlFileParameterMatchCache paramMatchCache;
        private final List<WindupVertexFrame> resultLocations;

        XmlFilePersistXPathFunction(GraphRewrite event, EvaluationContext context, XmlFileModel xml, XmlFileEvaluationStrategy evaluationStrategy,
                    ParameterStore store,
                    XmlFileParameterMatchCache paramMatchCache, List<WindupVertexFrame> resultLocations)
        {
            this.event = event;
            this.context = context;
            this.xml = xml;
            this.evaluationStrategy = evaluationStrategy;
            this.store = store;
            this.paramMatchCache = paramMatchCache;
            this.resultLocations = resultLocations;
        }

        @Override
        public Object evaluate(@SuppressWarnings("rawtypes") List args) throws XPathFunctionException
        {
            int frameIdx = ((Double) args.get(0)).intValue();
            NodeList arg1 = (NodeList) args.get(1);
            String nodeText = XmlUtil.nodeListToString(arg1);
            LOG.fine("persist(" + frameIdx + ", " + nodeText + ")");

            for (int i = 0; i < arg1.getLength(); i++)
            {
                Node node = arg1.item(i);
                if (xpathResultMatch != null)
                {
                    if (!node.toString().matches(xpathResultMatch))
                    {
                        continue;
                    }
                }
                // Everything passed for this Node. Start creating XmlTypeReferenceModel for it.
                int lineNumber = (int) node.getUserData(
                            LocationAwareContentHandler.LINE_NUMBER_KEY_NAME);
                int columnNumber = (int) node.getUserData(
                            LocationAwareContentHandler.COLUMN_NUMBER_KEY_NAME);

                GraphService<XmlTypeReferenceModel> fileLocationService = new GraphService<>(
                            event.getGraphContext(),
                            XmlTypeReferenceModel.class);
                XmlTypeReferenceModel fileLocation = fileLocationService.create();
                String sourceSnippit = XmlUtil.nodeToString(node);
                fileLocation.setSourceSnippit(sourceSnippit);
                fileLocation.setLineNumber(lineNumber);
                fileLocation.setColumnNumber(columnNumber);
                fileLocation.setLength(node.toString().length());
                fileLocation.setFile(xml);
                fileLocation.setXpath(xpathString);
                GraphService<NamespaceMetaModel> metaModelService = new GraphService<>(
                            event.getGraphContext(),
                            NamespaceMetaModel.class);
                for (Map.Entry<String, String> namespace : namespaces.entrySet())
                {
                    NamespaceMetaModel metaModel = metaModelService.create();
                    metaModel.setSchemaLocation(namespace.getKey());
                    metaModel.setSchemaLocation(namespace.getValue());
                    metaModel.addXmlResource(xml);
                    fileLocation.addNamespace(metaModel);
                }
                resultLocations.add(fileLocation);

                evaluationStrategy.modelSubmissionRejected();
                evaluationStrategy.modelMatched();
                if (fileNamePattern != null)
                {
                    if (!fileNamePattern.parse(xml.getFileName()).submit(event, context))
                    {
                        evaluationStrategy.modelSubmissionRejected();
                        continue;
                    }
                }

                for (Map.Entry<String, String> entry : paramMatchCache.getVariables(frameIdx).entrySet())
                {
                    Parameter<?> param = store.get(entry.getKey());
                    String value = entry.getValue();
                    if (!evaluationStrategy.submitValue(param, value))
                    {
                        evaluationStrategy.modelSubmissionRejected();
                        return false;
                    }
                }
                evaluationStrategy.modelSubmitted(fileLocation);
                evaluationStrategy.modelMatched();
            }

            return true;
        }
    }

}