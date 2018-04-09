package com.hiddenswitch.spellsource.util;

import com.hiddenswitch.spellsource.client.models.CompactChange;
import com.hiddenswitch.spellsource.client.models.CompactChangeDocument;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface DiffContext<T, K extends Comparable> {
	/**
	 * Indicates that the given ID should be removed.
	 *
	 * @param id The ID of the record to remove.
	 */
	void removed(K id);

	/**
	 * Indicates that the new document with the given ID should be inserted before the given {@code beforeId}. If {@code
	 * beforeId} is {@code null}, this document should be added to the very end of the list.
	 *
	 * @param newDocId
	 * @param newDoc
	 * @param beforeId The document to insert {@code newDoc} before, or {@code null} if the document should be added to
	 *                 the end of the list.
	 */
	void addedBefore(K newDocId, T newDoc, @Nullable K beforeId);

	void added(K newDocId, T newDoc);

	void possiblyChanged(K newDocId, T oldDoc, T newDoc);

	void movedBefore(K id, @Nullable K beforeId);

	Function<T, K> getKeyer();

	default Object lock() {
		return this;
	}
}
