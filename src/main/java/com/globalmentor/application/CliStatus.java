/*
 * Copyright © 2022 GlobalMentor, Inc. <http://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.globalmentor.application;

import static com.globalmentor.collections.iterators.Iterators.*;
import static java.lang.String.format;
import static java.util.Collections.*;
import static java.util.Objects.*;
import static java.util.concurrent.Executors.*;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.*;

/**
 * Manages a status line for a CLI application, along with the elapsed time, optional counter, and status message based upon current work in progress.
 * <p>
 * This status printer must be closed after it is no longer in use.
 * </p>
 * @param <W> The type identifier of work being performed. This may be a simple identifier, such as a file path.
 * @implSpec Actual printing synchronizes on the class instance, so subclasses desiring to print an additional status can also synchronize on the instance.
 * @implNote This implementation uses a separate, single-threaded executor for printing to reduce contention and prevent race conditions in status consistency.
 * @implNote This implementation catches any {@link IOException} thrown by the output sink and rethrows it as an {@link UncheckedIOException}. This is usually
 *           of no consequence as the underlying {@link System#out} or {@link System#err} will have caught and dealt with the {@link IOException} instead of
 *           throwing it anyway. If in the future other output sinks such as {@link Reader} are used with a requirement for better error handling, this approach
 *           can be improved.
 * @author Garret Wilson
 */
public class CliStatus<W> implements Closeable {

	private final ExecutorService printExecutorService = newSingleThreadExecutor();

	private final Appendable out;

	/** @return The output sink for printing the status. */
	protected Appendable getOut() {
		return out;
	}

	private final AtomicLong counter = new AtomicLong(0);

	/**
	 * Returns the counter used in the status.
	 * @apiNote While it is possible to access the counter directly, it is preferable to use one of the other methods such as {@link #incrementCount()}.
	 * @return The counter of current work encountered or completed. If the counter has a negative value, is is not included in the status.
	 */
	protected AtomicLong getCounter() {
		return counter;
	}

	/**
	 * Increments the counter and asynchronously updates the status.
	 * @see #printStatusAsync()
	 */
	public void incrementCount() {
		counter.incrementAndGet();
		printStatusAsync();
	}

	private final long startTimeNs;

	/** @return The duration of time elapsed. */
	public Duration getElapsedTime() {
		return Duration.ofNanos(System.nanoTime() - startTimeNs);
	}

	/** No-args constructor starting at the current time and printing to {@link System#out}. */
	public CliStatus() {
		this(System.out);
	}

	/**
	 * Constructor starting at the current time and printing to a custom out.
	 * @param out The output sink for printing the status.
	 */
	public CliStatus(@Nonnull final Appendable out) {
		this(out, System.nanoTime());
	}

	/**
	 * Start time constructor printing to {@link System#out}.
	 * @param startTimeNs The time the process started in nanoseconds.
	 */
	public CliStatus(final long startTimeNs) {
		this(System.out, startTimeNs);
	}

	/**
	 * Full constructor.
	 * @param out The output sink for printing the status.
	 * @param startTimeNs The time the process started in nanoseconds.
	 */
	public CliStatus(@Nonnull final Appendable out, final long startTimeNs) {
		this.out = requireNonNull(out);
		this.startTimeNs = startTimeNs;
	}

	/**
	 * The identifiers of work currently in progress. This is not expected to grow very large, as the number is limited to large extent by the number of threads
	 * used in the thread pool.
	 */
	private final Set<W> workInProgress = newSetFromMap(new ConcurrentHashMap<>());

	private Optional<W> optionalStatusWork = Optional.empty();

	/**
	 * Finds the work marked as the "current" one for the purpose of status display. This is the work to display in the status, even though there might be other
	 * work in progress.
	 * @implSpec This method updates the record of the current work dynamically, based upon whether the work is still recorded as being in progress. If it is not,
	 *           or if there is no record of the current work to use, another work is determined by choosing any from the set of work currently in progress.
	 * @return The file marked has currently having its content fingerprint generated for status purposes, if any.
	 */
	protected synchronized Optional<W> findStatusWork() {
		Optional<W> foundStatusWork = optionalStatusWork;
		//if no work has been chosen, or it is no longer actually in progress
		if(!optionalStatusWork.map(workInProgress::contains).orElse(false)) {
			foundStatusWork = findNext(workInProgress.iterator()); //chose an arbitrary work for the status
			optionalStatusWork = foundStatusWork; //update the record of the status work for next time
		}
		return foundStatusWork;
	}

	/**
	 * Adds a record of ongoing work. The status will later be updated asynchronously if appropriate. If the work was already recorded as ongoing, no further
	 * action is taken.
	 * @param work The ongoing work to add.
	 * @see #printStatusAsync()
	 */
	public void addWork(@Nonnull final W work) {
		if(workInProgress.add(requireNonNull(work))) {
			printStatusAsync();
		}
	}

	/**
	 * Removes any record of ongoing work. The status will later be updated asynchronously if appropriate. If the work was not recorded as ongoing, no further
	 * action is taken.
	 * @param work The ongoing work to remove.
	 * @see #printStatusAsync()
	 */
	public void removeWork(@Nonnull final W work) {
		if(workInProgress.remove(work)) {
			printStatusAsync();
		}
	}

	/**
	 * Returns a string representing the current work.
	 * @apiNote Normally this method simply identifies the work, but a subclass could override this method and provide some more detailed message potentially
	 *          using other state.
	 * @implSpec The default implementation merely delegates to the work's {@link Object#toString()}.
	 * @param work The work to display in the status.
	 * @return A status string for the work.
	 */
	protected String toStatus(@Nonnull final W work) {
		return work.toString();
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) a new line to appear above the status, scrolling previous information up.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @param line The line to print.
	 * @see #printStatus()
	 */
	public void printLineAsync(@Nonnull final String line) {
		printExecutorService.execute(() -> printLine(line));
	}

	/**
	 * Prints a new line to appear above the status, scrolling previous information up.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @apiNote Normally applications and subclasses will not call this method directly. Instead they should schedule the printing later using
	 *          {@link #printLineAsync(String)}.
	 * @implSpec The implementation prints a line over the current status, skips to the next line, and prints the current status. This has the effect of scrolling
	 *           information up and printing a line above the status.
	 * @param line The line to print.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 * @see #printStatus()
	 */
	protected synchronized void printLine(@Nonnull final String line) {
		final int padWidth = lastStatus != null ? lastStatus.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
		try {
			getOut().append(format("\r%-" + padWidth + "s%s", line, System.lineSeparator()));
		} catch(final IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
		lastStatus = null; //we're skipping to another line for further status
		printStatus();
	}

	/**
	 * Clears the current status line by blanking out the status and returning the cursor to the beginning of the line. The cursor does not move to the next line.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 */
	protected synchronized void clearStatusLine() {
		final int padWidth = lastStatus != null ? lastStatus.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
		try {
			getOut().append(format("\r%-" + padWidth + "s\r", ""));
		} catch(final IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
		lastStatus = null;
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) the current status, including the elapsed time, count, and current status file.
	 * @see #printStatus()
	 */
	public void printStatusAsync() {
		printExecutorService.execute(this::printStatus);
	}

	/** Keeps track of the last status string to prevent unnecessary re-printing and to determine padding. */
	@Nullable
	private String lastStatus = null;

	/**
	 * Prints the current status, including the elapsed time, optional count, and current work.
	 * @apiNote Normally applications and subclasses will not call this method directly. Instead they should schedule the status printing later using
	 *          {@link #printStatusAsync()}.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 * @see #getElapsedTime()
	 * @see #getCounter()
	 * @see #findStatusWork()
	 * @see #toStatus(Object)
	 */
	protected synchronized void printStatus() {
		final StringBuilder statusStringBuilder = new StringBuilder();
		//elapsed time
		final Duration elapsedTime = getElapsedTime();
		statusStringBuilder.append(format("%d:%02d:%02d", elapsedTime.toHours(), elapsedTime.toMinutesPart(), elapsedTime.toSecondsPart()));
		//count
		final long count = getCounter().get();
		if(count >= 0) {
			statusStringBuilder.append(" | ").append(count);
		}
		//work status
		findStatusWork().map(this::toStatus).ifPresent(workStatus -> statusStringBuilder.append(" | ").append(workStatus));
		final String status = statusStringBuilder.toString();
		if(!status.equals(lastStatus)) { //if the status is different than the last time (or there was no previous status)
			//We only have to pad to the last actual status, _not_ to the _padded_ last status, because
			//if the last status was printed padded, it would have erased the previous status already.
			//In other words, padding only needs to be added once to overwrite each previous status.
			final int padWidth = lastStatus != null ? lastStatus.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
			try {
				getOut().append(format("\r%-" + padWidth + "s", status));
			} catch(final IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
			lastStatus = status; //update the last status for checking the next time
		}
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation clears the status line and then shuts down the executor used for printing the status.
	 * @throws IOException If the status print executor could not be shut down.
	 */
	@Override
	public void close() throws IOException {
		printExecutorService.execute(this::clearStatusLine);
		printExecutorService.shutdown();
		try {
			if(!printExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
				printExecutorService.shutdownNow();
				if(!printExecutorService.awaitTermination(3, TimeUnit.SECONDS)) {
					throw new IOException("Status printing service not shut down properly.");
				}
			}
		} catch(final InterruptedException interruptedException) {
			printExecutorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

}
