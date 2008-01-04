package numfum.j2me.util;

/**
 *	QuickSort algorithm adapted from James Gosling's 1995 demo code.
 */
public final class QuickSort {
	private QuickSort() {}
	
	/**
	 *	Sorts the passed array of Comparable objects into natural order.
	 *
	 *	@see Comparable
	 */
	public static void sort(Comparable array[]) {
		sort(array, 0, array.length - 1);
	}
	
	public static void sort(Comparable[] array, int _lo, int _hi) {
		int lo = _lo;
		int hi = _hi;
		if (lo >= hi) {
			return;
		}
		
		Comparable swapped;
		/*
		 *  Special case for two elements.
		 */
		if(lo == hi - 1) {
			if (array[lo].compareTo(array[hi]) > 0) {
				swapped   = array[lo];
				array[lo] = array[hi];
				array[hi] = swapped;
			}
			return;
		}
		
		int n = (lo + hi) / 2;
		Comparable pivot = array[n];
		array[n]  = array[hi];
		array[hi] = pivot;
		while(lo < hi) {
			/*
			 *  Search forward from array[lo] until an element is found that
			 *  is greater than the pivot or lo >= hi.
			 */
			while (array[lo].compareTo(pivot) <= 0 && lo < hi) {
				lo++;
			}
			/*
			 *  Search backward from array[hi] until element is found that is
			 *  less than the pivot, or lo >= hi.
			 */
			 while (pivot.compareTo(array[hi]) <= 0 && lo < hi ) {
				hi--;
			}
			/*
			 *  Swap elements array[lo] and array[hi].
			 */
			 if(lo < hi) {
				swapped   = array[lo];
				array[lo] = array[hi];
				array[hi] = swapped;
			}
		}
		/*
		 *  Put the median in the 'centre' of the list.
		 */
		array[_hi] = array[hi];
		array[ hi] = pivot;
		/*
		 *  Recursive calls, elements array[_lo] to array[lo - 1] are less
		 *  than or equal to pivot, elements array[hi + 1] to array[_hi] are
		 *  greater than pivot.
		 */
		sort(array, _lo, lo - 1);
		sort(array, hi + 1, _hi);
	}
}