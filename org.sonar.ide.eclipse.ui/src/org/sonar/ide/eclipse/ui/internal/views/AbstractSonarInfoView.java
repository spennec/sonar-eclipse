/*
 * Sonar Eclipse
 * Copyright (C) 2010-2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.ui.internal.views;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.ViewPart;
import org.sonar.ide.eclipse.core.internal.resources.ResourceUtils;
import org.sonar.ide.eclipse.core.resources.ISonarResource;
import org.sonar.ide.eclipse.ui.internal.SonarImages;
import org.sonar.ide.eclipse.ui.internal.util.SelectionUtils;

/**
 * Abstract class for views which show information for a given element. Inspired by org.eclipse.jdt.internal.ui.infoviews.AbstractInfoView
 *
 * @since 0.3
 */
public abstract class AbstractSonarInfoView extends ViewPart implements ISelectionListener {

  protected ISonarResource currentViewInput;

  private boolean linking = true;

  private LinkAction toggleLinkAction;

  /**
   * The last selected element if linking was disabled.
   */
  private ISonarResource lastSelection;

  private final IPartListener2 partListener = new IPartListener2() {
    public void partVisible(IWorkbenchPartReference ref) {
      if (ref.getId().equals(getSite().getId())) {
        IWorkbenchPart activePart = ref.getPage().getActivePart();
        if (activePart != null) {
          selectionChanged(activePart, ref.getPage().getSelection());
        }
      }
    }

    public void partHidden(IWorkbenchPartReference ref) {
    }

    public void partInputChanged(IWorkbenchPartReference ref) {
      if (!ref.getId().equals(getSite().getId())) {
        computeAndSetInput(ref.getPart(false));
      }
    }

    public void partActivated(IWorkbenchPartReference ref) {
    }

    public void partBroughtToTop(IWorkbenchPartReference ref) {
    }

    public void partClosed(IWorkbenchPartReference ref) {
    }

    public void partDeactivated(IWorkbenchPartReference ref) {
    }

    public void partOpened(IWorkbenchPartReference ref) {
    }
  };

  /**
   * Create the part control.
   *
   * @param parent
   *          the parent control
   * @see IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
   */
  protected abstract void internalCreatePartControl(Composite parent);

  /**
   * @return the view's primary control.
   */
  protected abstract Control getControl();

  /**
   * Set the input of this view.
   */
  protected abstract void doSetInput(Object input);

  @Override
  public final void createPartControl(Composite parent) {
    internalCreatePartControl(parent);
    createActions();
    createToolbar();

    getSite().getWorkbenchWindow().getPartService().addPartListener(partListener);
    startListeningForSelectionChanges();
  }

  protected void createActions() {
    toggleLinkAction = new LinkAction();
  }

  private void createToolbar() {
    IToolBarManager toolbarManager = getViewSite().getActionBars().getToolBarManager();
    toolbarManager.add(toggleLinkAction);
    toolbarManager.add(new Separator());
    toolbarManager.update(false);
  }

  private class LinkAction extends Action {
    public LinkAction() {
      super("Link with Selection", SWT.TOGGLE);
      setTitleToolTip("Link with Selection");
      setImageDescriptor(SonarImages.SONARSYNCHRO_IMG);
      setChecked(isLinkingEnabled());
    }

    @Override
    public void run() {
      setLinkingEnabled(!isLinkingEnabled());
    }
  }

  /**
   * Sets whether this info view reacts to selection changes in the workbench.
   *
   * @param enabled
   *          if true then the input is set on selection changes
   */
  protected void setLinkingEnabled(boolean enabled) {
    linking = enabled;
    if (linking && (lastSelection != null)) {
      setInput(lastSelection);
    }
  }

  /**
   * Returns whether this info view reacts to selection changes in the workbench.
   *
   * @return true if linking with selection is enabled
   */
  protected boolean isLinkingEnabled() {
    return linking;
  }

  @Override
  public final void setFocus() {
    getControl().setFocus();
  }

  protected void startListeningForSelectionChanges() {
    getSite().getPage().addPostSelectionListener(this);
  }

  protected void stopListeningForSelectionChanges() {
    getSite().getPage().removePostSelectionListener(this);
  }

  public void selectionChanged(IWorkbenchPart part, ISelection selection) {
    if (this.equals(part)) {
      return;
    }
    if (!linking) {
      ISonarResource sonarResource = findSelectedSonarResource(part, selection);
      if (sonarResource != null) {
        lastSelection = sonarResource;
      }
    } else {
      lastSelection = null;
      computeAndSetInput(part);
    }
  }

  private void computeAndSetInput(final IWorkbenchPart part) {
    ISelectionProvider provider = part.getSite().getSelectionProvider();
    if (provider == null) {
      return;
    }
    final ISelection selection = provider.getSelection();
    if ((selection == null) || selection.isEmpty()) {
      return;
    }
    final ISonarResource input = findSelectedSonarResource(part, selection);
    if (isIgnoringNewInput(input)) {
      return;
    }
    if (input == null) {
      return;
    }
    setInput(input);
  }

  private void setInput(ISonarResource input) {
    currentViewInput = input;
    doSetInput(input);
  }

  /**
   * Finds and returns the Sonar resource selected in the given part.
   */
  private ISonarResource findSelectedSonarResource(IWorkbenchPart part, ISelection selection) {
    if (part instanceof EditorPart) {
      EditorPart editor = (EditorPart) part;
      IEditorInput editorInput = editor.getEditorInput();
      IResource resource = ResourceUtil.getResource(editorInput);
      return ResourceUtils.adapt(resource);
    } else if (selection instanceof IStructuredSelection) {
      return ResourceUtils.adapt(SelectionUtils.getSingleElement(selection));
    }
    return null;
  }

  /**
   * @return input input of this view or <code>null</code> if no input is set
   */
  protected ISonarResource getInput() {
    return currentViewInput;
  }

  protected boolean isIgnoringNewInput(ISonarResource sonarResource) {
    return (currentViewInput != null) && currentViewInput.equals(sonarResource) && (sonarResource != null);
  }

  @Override
  public void dispose() {
    stopListeningForSelectionChanges();
    getSite().getWorkbenchWindow().getPartService().removePartListener(partListener);

    super.dispose();
  }
}
