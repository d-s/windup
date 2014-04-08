package org.jboss.windup.engine.visitor.reporter;

import javax.inject.Inject;

import org.jboss.windup.engine.visitor.AbstractGraphVisitor;
import org.jboss.windup.engine.visitor.VisitorPhase;
import org.jboss.windup.graph.dao.JavaClassDao;
import org.jboss.windup.graph.model.resource.ArchiveEntryResource;
import org.jboss.windup.graph.model.resource.JavaClass;
import org.jboss.windup.graph.model.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Find situations where a class is provided by multiple archives.
 * 
 * @author bradsdavis@gmail.com
 * 
 */
public class DuplicateClassReporter extends AbstractGraphVisitor
{

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateClassReporter.class);

    @Inject
    private JavaClassDao javaClassDao;

    @Override
    public VisitorPhase getPhase()
    {
        return VisitorPhase.Reporting;
    }

    @Override
    public void run()
    {
        for (JavaClass clz : javaClassDao.getAllDuplicateClasses())
        {
            LOG.info("Duplicate class: " + clz.getQualifiedName());

            for (Resource resource : clz.getResources())
            {
                if (resource instanceof ArchiveEntryResource)
                {
                    ArchiveEntryResource ar = (ArchiveEntryResource) resource;
                    LOG.info(" - Provided by: " + ar.getArchive().getArchiveName() + " -> " + clz.getQualifiedName());
                }
            }
        }
    }
}
