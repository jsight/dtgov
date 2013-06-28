/**
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.overlord.dtgov.jbpm.ejb;

import static org.kie.scanner.MavenRepository.getMavenRepository;

import java.util.Collection;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.transaction.Status;
import javax.transaction.UserTransaction;

import org.apache.maven.project.MavenProject;
import org.jbpm.services.task.wih.NonManagedLocalHTWorkItemHandler;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.internal.runtime.manager.cdi.qualifier.Singleton;
import org.kie.internal.runtime.manager.context.EmptyContext;
import org.kie.scanner.MavenRepository;
import org.overlord.dtgov.jbpm.util.KieUtil;
import org.overlord.sramp.governance.Governance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.artifact.Artifact;

@Stateless
@TransactionManagement(TransactionManagementType.BEAN)
public class ProcessBean implements ProcessLocal {

	private KieContainer kieContainer = null;
	KieSession ksession = null;
	private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private UserTransaction ut;

    @Inject
    @Singleton
    RuntimeManager singletonManager;

    @Inject
    TaskService taskService;

    @PreDestroy
    public void preDestroy() {
    	getSession().dispose();
    }

    @Override
    public long startProcess(String processId, Map<String, Object> parameters) throws Exception {


    	ksession = getSession();

    	//HT handler
    	NonManagedLocalHTWorkItemHandler humanTaskHandler = new NonManagedLocalHTWorkItemHandler(ksession, taskService);


        ksession.getWorkItemManager().registerWorkItemHandler("Human Task", humanTaskHandler);

    	ksession.getWorkItemManager().registerWorkItemHandler("HttpClientDeploy", new org.overlord.dtgov.jbpm.util.HttpClientWorkItemHandler());
    	ksession.getWorkItemManager().registerWorkItemHandler("HttpClientNotify", new org.overlord.dtgov.jbpm.util.HttpClientWorkItemHandler());
    	ksession.getWorkItemManager().registerWorkItemHandler("HttpClientUpdateMetaData", new org.overlord.dtgov.jbpm.util.HttpClientWorkItemHandler());

        long processInstanceId = -1;
        ut.begin();
        try {
            // start a new process instance
            ProcessInstance processInstance = ksession.startProcess(processId, parameters);
            processInstanceId = processInstance.getId();
            logger.info("Process started ... : processInstanceId = " + processInstanceId);
            ut.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (ut.getStatus() == Status.STATUS_ACTIVE) {
                ut.rollback();
            }
            throw e;
        }
        return processInstanceId;
    }

    @Override
    public Collection<ProcessInstance> listProcessInstances() throws Exception {

    	RuntimeEngine runtime = singletonManager.getRuntimeEngine(EmptyContext.get());

        KieSession ksession = runtime.getKieSession();

        Collection<ProcessInstance> processInstances = null;
        ut.begin();

        try {
	        processInstances = ksession.getProcessInstances();
	        for (ProcessInstance processInstance : processInstances) {
	        	logger.info(processInstance.getProcess().getName());

				System.out.println("..");
			}
	        ut.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (ut.getStatus() == Status.STATUS_ACTIVE) {
                ut.rollback();
            }
            throw e;
        } finally {
        	ksession.dispose();
        }
        return processInstances;

    }

 @Override
public void listProcessInstanceDetail(long processId) throws Exception {


 	    KieSession ksession = getSession();
 	   logger.info("ksession=" + ksession);

        ut.begin();

        try {
	        ProcessInstance processInstance = ksession.getProcessInstance(processId);
	        if (processInstance!=null) {
				System.out.println(processInstance.getProcess().getName());
				System.out.println(processInstance.getState());

				System.out.println("..");
	        }
	        ut.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (ut.getStatus() == Status.STATUS_ACTIVE) {
                ut.rollback();
            }
            throw e;
        }


    }

	public synchronized KieSession getSession() {

		if (kieContainer==null) {
			Governance governance = new Governance();
	    	try {
	    		String srampUrl = governance.getSrampUrl().toExternalForm();
	        	srampUrl = "sramp" + srampUrl.substring(srampUrl.indexOf(":"));

		    	KieServices ks = KieServices.Factory.get();
		    	MavenProject srampProject = KieUtil.getSrampProject(
		    			governance.getSrampWagonVersion(),
		    			srampUrl,
		    			governance.getSrampWagonSnapshots(),
		    			governance.getSrampWagonReleases());

		    	MavenRepository repo = getMavenRepository();
		    	//MavenRepository repo = getMavenRepository(srampProject);
		    	ReleaseId releaseId = ks.newReleaseId(
		    			governance.getGovernanceWorkflowGroup(),
		    			governance.getGovernanceWorkflowName(),
		    			governance.getGovernanceWorkflowVersion());

		        String name = releaseId.toExternalForm();
		        Artifact artifact = repo.resolveArtifact(name);
		        logger.info("Creating KIE container with workflows from " + artifact);
		    	kieContainer = ks.newKieContainer(releaseId);
		    	ksession = kieContainer.newKieSession(governance.getGovernanceWorkflowSession());
	    	} catch (Exception e) {
	    		logger.error(e.getMessage(),e);
	    	}
		}

		return ksession;
	}



}
