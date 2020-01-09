/*******************************************************************************
 * Copyright (c) 2004, 2009 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.tasks.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.window.Window;
import org.eclipse.mylyn.internal.provisional.commons.ui.AbstractNotification;
import org.eclipse.mylyn.internal.tasks.ui.notifications.TaskListNotificationPopup;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * @author Rob Elves
 */
public class TaskListNotificationManager implements IPropertyChangeListener {

	private static final long DELAY_OPEN = 5 * 1000;

	private static final boolean runSystem = true;

	private TaskListNotificationPopup popup;

	private final Set<AbstractNotification> notifications = new HashSet<AbstractNotification>();

	private final Set<AbstractNotification> currentlyNotifying = Collections.synchronizedSet(notifications);

	private final List<ITaskListNotificationProvider> notificationProviders = new ArrayList<ITaskListNotificationProvider>();

	private final WeakHashMap<Object, Object> cancelledTokens = new WeakHashMap<Object, Object>();

	private final Job openJob = new Job(Messages.TaskListNotificationManager_Open_Notification_Job) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {

				if (Platform.isRunning() && PlatformUI.getWorkbench() != null
						&& PlatformUI.getWorkbench().getDisplay() != null
						&& !PlatformUI.getWorkbench().getDisplay().isDisposed()) {
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

						public void run() {
							collectNotifications();

							if (popup != null && popup.getReturnCode() == Window.CANCEL) {
								List<AbstractNotification> notifications = popup.getNotifications();
								for (AbstractNotification notification : notifications) {
									if (notification.getToken() != null) {
										cancelledTokens.put(notification.getToken(), null);
									}
								}
							}

							for (Iterator<AbstractNotification> it = currentlyNotifying.iterator(); it.hasNext();) {
								AbstractNotification notification = it.next();
								if (notification.getToken() != null
										&& cancelledTokens.containsKey(notification.getToken())) {
									it.remove();
								}
							}

							synchronized (TaskListNotificationManager.class) {
								if (currentlyNotifying.size() > 0) {
//										popup.close();
									showPopup();
								}
							}
						}
					});
				}
			} finally {
				if (popup != null) {
					schedule(popup.getDelayClose() / 2);
				} else {
					schedule(DELAY_OPEN);
				}
			}

			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			return Status.OK_STATUS;
		}

	};

	public TaskListNotificationManager() {
	}

	public void showPopup() {
		if (popup != null) {
			popup.close();
		}

		Shell shell = new Shell(PlatformUI.getWorkbench().getDisplay());
		popup = new TaskListNotificationPopup(shell);
		popup.setFadingEnabled(TasksUiInternal.isAnimationsEnabled());
		List<AbstractNotification> toDisplay = new ArrayList<AbstractNotification>(currentlyNotifying);
		Collections.sort(toDisplay);
		popup.setContents(toDisplay);
		cleanNotified();
		popup.setBlockOnOpen(false);
		popup.open();
	}

	private void cleanNotified() {
		currentlyNotifying.clear();
	}

	/** public for testing */
	public void collectNotifications() {
		for (ITaskListNotificationProvider provider : notificationProviders) {
			currentlyNotifying.addAll(provider.getNotifications());
		}
	}

	public void startNotification(long initialStartupTime) {
		if (TasksUiPlugin.getDefault().getPreferenceStore().getBoolean(
				ITasksUiPreferenceConstants.NOTIFICATIONS_ENABLED)) {
			if (!openJob.cancel()) {
				try {
					openJob.join();
				} catch (InterruptedException e) {
					// ignore
				}
			}
			openJob.setSystem(runSystem);
			openJob.schedule(initialStartupTime);
		}
	}

	public void stopNotification() {
		openJob.cancel();
//		closeJob.cancel();
//		if (popup != null) {
//			popup.close();
//		}
	}

	public void addNotificationProvider(ITaskListNotificationProvider notification_provider) {
		notificationProviders.add(notification_provider);
	}

	public void removeNotificationProvider(ITaskListNotificationProvider notification_provider) {
		notificationProviders.remove(notification_provider);
	}

	/**
	 * public for testing purposes
	 */
	public Set<AbstractNotification> getNotifications() {
		synchronized (TaskListNotificationManager.class) {
			return currentlyNotifying;
		}
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(ITasksUiPreferenceConstants.NOTIFICATIONS_ENABLED)) {
			Object newValue = event.getNewValue();
			if (!(newValue instanceof Boolean)) {
				// default if no preference value
				startNotification(0);
			} else if ((Boolean) newValue == true) {
				startNotification(0);
			} else {
				stopNotification();
			}
		}
	}
}
