/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import static com.android.AndroidConstants.FD_RES_LAYOUT;
import static com.android.sdklib.SdkConstants.FD_RES;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectCreationPage;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewProjectWizard;
import com.android.ide.eclipse.adt.internal.wizards.newproject.NewTestProjectCreationPage;
import com.android.ide.eclipse.tests.SdkTestCase;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

@SuppressWarnings("restriction")
public class AdtProjectTest extends SdkTestCase {
    /**
     * Individual tests don't share an instance of the TestCase so we stash the test
     * project in a static field such that we don't need to keep recreating it -- should
     * be much faster.
     */
    protected static IProject sProject;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (sProject == null) {
            IProject project = null;
            String projectName = "testproject-" + System.currentTimeMillis();
            project = createProject(projectName);
            assertNotNull(project);
            sProject = project;
        }
    }

    protected IProject getProject() {
        return sProject;
    }

    protected IFile getTestFile(IProject project, String name) throws Exception {
        IFolder resFolder = project.getFolder(FD_RES);
        NullProgressMonitor monitor = new NullProgressMonitor();
        if (!resFolder.exists()) {
            resFolder.create(true /* force */, true /* local */, monitor);
        }
        IFolder layoutfolder = resFolder.getFolder(FD_RES_LAYOUT);
        if (!layoutfolder.exists()) {
            layoutfolder.create(true /* force */, true /* local */, monitor);
        }
        IFile file = layoutfolder.getFile(name);
        if (!file.exists()) {
            String xml = readTestFile(name, true);
            InputStream bstream = new ByteArrayInputStream(xml.getBytes("UTF-8")); //$NON-NLS-1$
            file.create(bstream, false /* force */, monitor);
        }

        return file;
    }

    protected IProject createProject(String name) {
        IAndroidTarget target = null;

        IAndroidTarget[] targets = getSdk().getTargets();
        for (IAndroidTarget t : targets) {
            if (t.getVersion().getApiLevel() >= 11) {
                target = t;
                break;
            }
        }
        assertNotNull(target);

        final StubProjectWizard newProjCreator = new StubProjectWizard(
                name, target);
        newProjCreator.init(null, null);
        // need to run finish on ui thread since it invokes a perspective switch
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                newProjCreator.performFinish();
            }
        });

        return validateProjectExists(name);
    }

    public void createTestProject() {
        IAndroidTarget target = null;

        IAndroidTarget[] targets = getSdk().getTargets();
        for (IAndroidTarget t : targets) {
            if (t.getVersion().getApiLevel() >= 11) {
                target = t;
                break;
            }
        }
        assertNotNull(target);
    }

    private static IProject validateProjectExists(String name) {
        IProject iproject = getProject(name);
        assertTrue(String.format("%s project not created", name), iproject.exists());
        assertTrue(String.format("%s project not opened", name), iproject.isOpen());
        return iproject;
    }

    private static IProject getProject(String name) {
        IProject iproject = ResourcesPlugin.getWorkspace().getRoot()
                .getProject(name);
        return iproject;
    }

    public static ViewElementDescriptor createDesc(String name, String fqn, boolean hasChildren) {
        if (hasChildren) {
            return new ViewElementDescriptor(name, name, fqn, "", "", new AttributeDescriptor[0],
                    new AttributeDescriptor[0], new ElementDescriptor[1], false);
        } else {
            return new ViewElementDescriptor(name, fqn);
        }
    }

    public static UiViewElementNode createNode(UiViewElementNode parent, String fqn,
            boolean hasChildren) {
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);
        ViewElementDescriptor descriptor = createDesc(name, fqn, hasChildren);
        if (parent == null) {
            // All node hierarchies should be wrapped inside a document node at the root
            parent = new UiViewElementNode(createDesc("doc", "doc", true));
        }
        return (UiViewElementNode) parent.appendNewUiChild(descriptor);
    }

    public static UiViewElementNode createNode(String fqn, boolean hasChildren) {
        return createNode(null, fqn, hasChildren);
    }

    protected String readTestFile(String relativePath, boolean expectExists) {
        String path = "testdata" + File.separator + relativePath; //$NON-NLS-1$
        InputStream stream =
            AdtProjectTest.class.getResourceAsStream(path);
        if (!expectExists && stream == null) {
            return null;
        }

        assertNotNull(stream);

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String xml = AdtPlugin.readFile(reader);
        assertNotNull(xml);
        assertTrue(xml.length() > 0);

        return xml;
    }

    /** Special editor context set on the model to be rendered */
    protected static class TestLayoutEditor extends LayoutEditor {
        private final IFile mFile;
        private final IStructuredDocument mStructuredDocument;
        private UiDocumentNode mUiRootNode;

        public TestLayoutEditor(IFile file, IStructuredDocument structuredDocument,
                UiDocumentNode uiRootNode) {
            mFile = file;
            mStructuredDocument = structuredDocument;
            mUiRootNode = uiRootNode;
        }

        @Override
        public IFile getInputFile() {
            return mFile;
        }

        @Override
        public IProject getProject() {
            return mFile.getProject();
        }

        @Override
        public IStructuredDocument getStructuredDocument() {
            return mStructuredDocument;
        }

        @Override
        public UiDocumentNode getUiRootNode() {
            return mUiRootNode;
        }

        @Override
        public void editorDirtyStateChanged() {
        }

        @Override
        public IStructuredModel getModelForRead() {
            IModelManager mm = StructuredModelManager.getModelManager();
            if (mm != null) {
                try {
                    return mm.getModelForRead(mFile);
                } catch (Exception e) {
                    fail(e.toString());
                }
            }

            return null;
        }
    }

    /**
     * Stub class for project creation wizard.
     * <p/>
     * Created so project creation logic can be run without UI creation/manipulation.
     */
    public class StubProjectWizard extends NewProjectWizard {

        private final String mProjectName;
        private final IAndroidTarget mTarget;

        public StubProjectWizard(String projectName, IAndroidTarget target) {
            this.mProjectName = projectName;
            this.mTarget = target;
        }

        /**
         * Override parent to return stub page
         */
        @Override
        protected NewProjectCreationPage createMainPage() {
            return new StubProjectCreationPage(mProjectName, mTarget);
        }

        /**
         * Override parent to return null page
         */
        @Override
        protected NewTestProjectCreationPage createTestPage() {
            return null;
        }

        /**
         * Overrides parent to return dummy wizard container
         */
        @Override
        public IWizardContainer getContainer() {
            return new IWizardContainer() {

                public IWizardPage getCurrentPage() {
                    return null;
                }

                public Shell getShell() {
                    return null;
                }

                public void showPage(IWizardPage page) {
                    // pass
                }

                public void updateButtons() {
                    // pass
                }

                public void updateMessage() {
                    // pass
                }

                public void updateTitleBar() {
                    // pass
                }

                public void updateWindowTitle() {
                    // pass
                }

                /**
                 * Executes runnable on current thread
                 */
                public void run(boolean fork, boolean cancelable,
                        IRunnableWithProgress runnable)
                        throws InvocationTargetException, InterruptedException {
                    runnable.run(new NullProgressMonitor());
                }

            };
        }
    }

    /**
     * Stub class for project creation page.
     * <p/>
     * Returns canned responses for creating a sample project.
     */
    public class StubProjectCreationPage extends NewProjectCreationPage {

        private final String mProjectName;
        private final IAndroidTarget mTarget;

        public StubProjectCreationPage(String projectName, IAndroidTarget target) {
            super();
            this.mProjectName = projectName;
            this.mTarget = target;
            setTestInfo(null);
        }

        @Override
        public IMainInfo getMainInfo() {
            return new IMainInfo() {
                public String getProjectName() {
                    return mProjectName;
                }

                public String getPackageName() {
                    return "com.android.eclipse.tests";
                }

                public String getActivityName() {
                    return mProjectName;
                }

                public String getApplicationName() {
                    return mProjectName;
                }

                public boolean isNewProject() {
                    return true;
                }

                public String getSourceFolder() {
                    return "src";
                }

                public IPath getLocationPath() {
                    // Default location
                    return null;//new Path(mLocation);
                }

                public String getMinSdkVersion() {
                    return null;
                }

                public IAndroidTarget getSdkTarget() {
                    return mTarget;
                }

                public boolean isCreateActivity() {
                    return false;
                }

                public boolean useDefaultLocation() {
                    return true;
                }

                public IWorkingSet[] getSelectedWorkingSets() {
                    return new IWorkingSet[0];
                }
            };
        }
    }

    public void testDummy() {
        // This class contains shared test functionality for testcase subclasses,
        // but without an actual test in the class JUnit complains (even if we make
        // it abstract)
    }
}