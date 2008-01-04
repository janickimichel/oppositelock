package numfum.j2me.util;

/**
 *	This Comparable interface is here for compatibilty with J2ME
 *	implementations of the 'toolbox' packages. As of CLDC1.1 the java.lang
 *	package does not yet have its own Comparable (or its own sort) so where
 *	code is used on both ME and SE platforms it's preferable to use the
 *	QuickSort in this package to ensure the same behavior.
 */
public interface Comparable {
	public int compareTo(Object o);
}