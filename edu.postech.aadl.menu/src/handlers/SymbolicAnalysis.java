package handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.xtext.ui.editor.XtextEditor;

import edu.postech.aadl.synch.propspec.PropspecEditorResourceManager;
import edu.postech.aadl.xtext.propspec.propSpec.Property;
import edu.postech.aadl.xtext.propspec.propSpec.Top;
import edu.postech.maude.view.views.DisplayView;
import maude.Maude;

public class SymbolicAnalysis extends AbstractHandler {

	private String maudeDirPath;
	private String maudeExecPath;
	private String maudeOptions;
	private String maudeLibDir;
	private String aadlMaudePath;
	private String aadlMaudeBaseDir;
	private String aadlMaudeFullPath;

	private Top propSpecRes;
	private String propSpecFileName;

	private PropspecEditorResourceManager resManager;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		resManager = new PropspecEditorResourceManager();
		IWorkbenchPart part = HandlerUtil.getActivePart(event);

		XtextEditor xtextEditor = (part.getSite().getId().compareTo("edu.postech.aadl.xtext.propspec.PropSpec") == 0)
				&& (part instanceof XtextEditor) ? (XtextEditor) part : null;


		resManager.setEditor(xtextEditor);
		addPSPCListener();

		propSpecFileName = resManager.getEditorFile().getName();
		propSpecFileName = propSpecFileName.substring(0, propSpecFileName.indexOf("."));
		propSpecRes = getPropSpecResource(resManager.getEditorFile().getFullPath());

		IPreferenceStore pref = new ScopedPreferenceStore(InstanceScope.INSTANCE, "edu.postech.maude.preferences.page");
		maudeDirPath = pref.getString("MAUDE_DIRECTORY");
		maudeExecPath = pref.getString("MAUDE");
		maudeOptions = pref.getString("MAUDE_OPTIONS");
		maudeLibDir = pref.getString("MAUDE_LIBRARY_DIR");


		aadlMaudePath = resManager.getCodegenFilePath().toString();
		aadlMaudeBaseDir = resManager.getEditorFile().getLocation().removeLastSegments(3).toString();
		aadlMaudeFullPath = aadlMaudeBaseDir + aadlMaudePath;

		DisplayView.clearView();

		for (Property pr : propSpecRes.getProperty()) {
			Maude maude = new Maude();
			maude = maudeDefaultBuilder(maude);

			IPath pspcGeneratedMaudePath = resManager.getCodegenFilePath().removeLastSegments(1)
					.append(propSpecFileName + "-" + propSpecRes.getName() + "-" + pr.getName() + ".maude");
			maude.setTestFilePath(pspcGeneratedMaudePath);

			IPath resultPath = resManager.getCodegenFilePath().removeLastSegments(1).append("result")
					.append(propSpecFileName + "-" + pr.getName() + ".txt");

			maude.runMaude(resultPath, pr);
		}
		return null;
	}

	private void addPSPCListener() {
		IFile editor = resManager.getEditorFile();
		IPath resultDir = editor.getFullPath().removeLastSegments(2).append("verification").append("result");
		ResourcesPlugin.getWorkspace().addResourceChangeListener(event -> {
			if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
				if (event.getDelta() != null && event.getDelta().findMember(editor.getFullPath()) != null) {
					IResourceDelta delta = event.getDelta().findMember(editor.getFullPath());
					switch (delta.getKind()) {
						case IResourceDelta.CHANGED:
							Top top = getPropSpecResource(editor.getFullPath());
							DisplayView.refreshData(top.getProperty());
							break;
						case IResourceDelta.REMOVED:
							DisplayView.removeData(editor);
							break;
					}
				} else if (event.getDelta() != null && event.getDelta().findMember(resultDir) != null) {
					IResourceDelta delta = event.getDelta().findMember(resultDir);
					for(IResourceDelta result : delta.getAffectedChildren()) {
						switch(result.getKind()) {
						case IResourceDelta.REMOVED:
							DisplayView.removeData(result.getFullPath());
						}
					}
				}
			}
		});
	}

	private Maude maudeDefaultBuilder(Maude maude) {
		maude.setMaudeDirPath(maudeDirPath);
		maude.setMaudeExecPath(maudeExecPath);
		maude.setOption(maudeOptions);
		maude.setTargetMaude(aadlMaudeFullPath);
		maude.setPspcFile(resManager.getEditorFile());
		maude.setMaudeLibDirPath(maudeLibDir);

		return maude;
	}

	private Top getPropSpecResource(IPath path) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IResource ire = root.findMember(path);

		ResourceSet rs = new ResourceSetImpl();
		Resource resource = rs.getResource(URI.createURI(ire.getFullPath().toString()), true);
		EObject eo = resource.getContents().get(0);
		if (eo instanceof Top) {
			return (Top) eo;
		}
		return null;
	}
}