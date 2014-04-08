package org.jboss.windup.engine.visitor.reporter;

import java.io.File;
import java.io.FileOutputStream;

import javax.inject.Inject;

import org.jboss.windup.engine.visitor.AbstractGraphVisitor;
import org.jboss.windup.engine.visitor.VisitorPhase;
import org.jboss.windup.graph.WindupContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tinkerpop.blueprints.util.io.graphml.GraphMLWriter;

/**
 * Serializes the graph to GraphML.
 * 
 * @author bradsdavis@gmail.com
 * 
 */
public class GraphRenderReporter extends AbstractGraphVisitor
{
    private static final Logger LOG = LoggerFactory.getLogger(GraphRenderReporter.class);

    @Inject
    private WindupContext context;

    @Override
    public VisitorPhase getPhase()
    {
        return VisitorPhase.Reporting;
    }

    @Override
    public void run()
    {
        File graphLocation = new File("/home/jsightler/tmp/", "graphml.graphml");
        GraphMLWriter graphML = new GraphMLWriter(context.getGraphContext().getGraph());
        try
        {
            graphML.outputGraph(new FileOutputStream(graphLocation));
            LOG.info("Wrote graph to: " + graphLocation.getAbsolutePath());
        }
        catch (Exception e)
        {
            LOG.error("Exception writing graph: ", e);
        }
    }

}
