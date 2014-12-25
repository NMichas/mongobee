package com.github.mongobee.utils;

import static java.util.Arrays.asList;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.changeset.ChangeLog;
import com.github.mongobee.changeset.ChangeSet;

/**
 * Utilities to deal with reflections and annotations
 *
 * @author lstolowski
 * @since 27/07/2014
 */
public class ChangeService {
	private final String changeLogsBasePackage;
	private BundleContext bundleContext;
	private static final Logger LOGGER = Logger.getLogger(ChangeService.class.getName());
	
	public ChangeService(String changeLogsBasePackage, BundleContext bundleContext) {
		this.changeLogsBasePackage = changeLogsBasePackage;
		this.bundleContext = bundleContext;
	}

	public List<Class<?>> fetchChangeLogs() {
		BundleWiring bundleWiring = bundleContext.getBundle().adapt(BundleWiring.class);
		Collection<String> resources = bundleWiring.listResources(changeLogsBasePackage, "*.class", BundleWiring.LISTRESOURCES_RECURSE);		
		List<Class<?>> filteredChangeLogs = new ArrayList<>();
		for (String resource : resources) {
			try {
				resource = resource.replace("/", ".").replace(".class", "");
				Class c = bundleContext.getBundle().loadClass(resource);
				if (c.isAnnotationPresent(ChangeLog.class)) {
					filteredChangeLogs.add(c);
				}
			} catch (ClassNotFoundException e) {
				LOGGER.log(Level.SEVERE, "Could not load resource {0}.", resource);
			}
		}
		Collections.sort(filteredChangeLogs, new ChangeLogComparator());

		return filteredChangeLogs;
	}

	public List<Method> fetchChangeSets(final Class<?> type) {
		final List<Method> changeSets = filterChangeSetAnnotation(asList(type
				.getDeclaredMethods()));
		final List<Method> filteredChangeSets = (List<Method>) filterByActiveProfiles(changeSets);

		Collections.sort(filteredChangeSets, new ChangeSetComparator());

		return filteredChangeSets;
	}

	public boolean isRunAlwaysChangeSet(Method changesetMethod) {
		if (changesetMethod.isAnnotationPresent(ChangeSet.class)) {
			ChangeSet annotation = changesetMethod
					.getAnnotation(ChangeSet.class);
			return annotation.runAlways();
		} else {
			return false;
		}
	}

	public ChangeEntry createChangeEntry(Method changesetMethod) {
		if (changesetMethod.isAnnotationPresent(ChangeSet.class)) {
			ChangeSet annotation = changesetMethod
					.getAnnotation(ChangeSet.class);

			return new ChangeEntry(annotation.id(), annotation.author(),
					new Date(), changesetMethod.getDeclaringClass().getName(),
					changesetMethod.getName());
		} else {
			return null;
		}
	}

	private List<?> filterByActiveProfiles(
			Collection<? extends AnnotatedElement> annotated) {
		List<AnnotatedElement> filtered = new ArrayList<>();
		for (AnnotatedElement element : annotated) {
			filtered.add(element);
		}
		return filtered;
	}

	private List<Method> filterChangeSetAnnotation(List<Method> allMethods) {
		final List<Method> changesetMethods = new ArrayList<>();
		for (final Method method : allMethods) {
			if (method.isAnnotationPresent(ChangeSet.class)) {
				changesetMethods.add(method);
			}
		}
		return changesetMethods;
	}

}
