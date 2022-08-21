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
import static com.globalmentor.java.Conditions.*;
import static java.lang.String.format;
import static java.util.Collections.*;
import static java.util.Objects.*;
import static java.util.concurrent.CompletableFuture.*;
import static java.util.concurrent.Executors.*;
import static org.fusesource.jansi.Ansi.*;
import static org.slf4j.helpers.MessageFormatter.*;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.*;

import org.slf4j.Logger;
import org.slf4j.event.Level;

/**
 * Manages a status line for a CLI application, along with the elapsed time, optional counter, and status label based upon current work in progress. There are
 * three sources of the status label, explained at {@link #findStatusLabel()}.
 * <p>
 * The status by default is shown on {@link System#err}. This is primarily to prevent interference with the main output of the CLI application. See
 * <a href="https://unix.stackexchange.com/q/331611">Do progress reports/logging information belong on stderr or stdout?</a> for more discussion.
 * </p>
 * <p>
 * This status also function as an executor, allowing operations to be executed serially via {@link #execute(Runnable)}, which delegates to the internal
 * executor service.
 * </p>
 * <p>
 * If some operation might interfere with the status line (e.g. printing some information to the same output, or to another output that eventually is shown in
 * the same output, such as a status at {@link System#out} and an error on {@link System#err} but both displayed in the same terminal), the operation may be
 * serialized with the status using {@link #supplyWithoutStatusLineAsync(Runnable)} to prevent its output from corrupting the status line.
 * </p>
 * <p>
 * The status also enhances logging by providing methods such as {@link #warnAsync(Logger, String, Object...)} which not only ensure any output is serialized
 * and does not corrupt the status line; but also provides a status notification with the same message as is being logged if the particular log level is enabled
 * for the logger.
 * </p>
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
 * @see <a href="https://unix.stackexchange.com/q/331611">Do progress reports/logging information belong on stderr or stdout?</a>
 */
public class CliStatus<W> implements Executor, Closeable {

	/** The default notification duration. */
	protected static final Duration NOTIFICATION_DEFAULT_DURATION = Duration.ofSeconds(8);

	/** The default notification severity. */
	protected static final Level NOTIFICATION_DEFAULT_SEVERITY = Level.INFO;

	/** The longest work label to show without constraining its length. */
	protected static final int WORK_MAX_LABEL_LENGTH = 120;

	private final ExecutorService executorService = newSingleThreadExecutor();

	/** @return The executor service being used for queue operations. */
	protected ExecutorService getExecutorService() {
		return executorService;
	}

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
	 * @see #printStatusLineAsync()
	 */
	public void incrementCount() {
		counter.incrementAndGet();
		printStatusLineAsync();
	}

	private final AtomicLong total = new AtomicLong(-1);

	/**
	 * Returns the total used in the status.
	 * @return The total work count expected. If the total has a negative value, is is not included in the status.
	 * @see #getCounter()
	 */
	public long getTotal() {
		return total.get();
	}

	/**
	 * Sets the total expected for the counter.
	 * @param total The new total, or a negative value if no total should be displayed.
	 * @see #getCounter()
	 */
	public void setTotal(final long total) {
		this.total.set(total);
	}

	private final long startTimeNs;

	/** @return The duration of time elapsed. */
	public Duration getElapsedTime() {
		return Duration.ofNanos(System.nanoTime() - startTimeNs);
	}

	/** No-args constructor starting at the current time and printing to {@link System#err}. */
	public CliStatus() {
		this(System.err);
	}

	/**
	 * Constructor starting at the current time and printing to a custom out.
	 * @param out The output sink for printing the status.
	 */
	public CliStatus(@Nonnull final Appendable out) {
		this(out, System.nanoTime());
	}

	/**
	 * Start time constructor printing to {@link System#err}.
	 * @param startTimeNs The time the process started in nanoseconds.
	 */
	public CliStatus(final long startTimeNs) {
		this(System.err, startTimeNs);
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
	 * {@inheritDoc}
	 * @implSpec This implementation delegates to the internal executor service.
	 */
	@Override
	public void execute(final Runnable command) {
		getExecutorService().execute(command);
	}

	/**
	 * Serially executes some command that might interfere with status output, ensuring that the status is erased before the operation and then reprinted after
	 * the operation.
	 * <p>
	 * For example a log line might be written to the same output as the status, or it might be written to a different standard output that is being sent to the
	 * terminal along with the status. In either case writing the information concurrently would result in intermingled text. Executing the command via this
	 * method would ensure that the log message is written separately from the status update.
	 * </p>
	 * @param command The runnable task to execute while the status is suspended.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> supplyWithoutStatusLineAsync(final Runnable command) {
		return supplyAsync(() -> {
			clearStatusLine();
			command.run();
			return printStatusLine();
		}, getExecutorService());
	}

	/**
	 * Logs a message at {@link Level#TRACE} using the specified logger, ensuring that the status line is first cleared to prevent any output interference in case
	 * logging is ultimately going to the same destination as the status, and then sets the notification to the same formatted string using the same log level if
	 * the log level is enabled for the logger.
	 * @apiNote This is a convenience method that uses the default notification duration. For more control over logging and notification duration, schedule manual
	 *          logging and notification updates using {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @param logger The logger to use for logging the formatted message.
	 * @param format The string to be formatted.
	 * @param arguments The arguments to the format string.
	 * @return The future updated status line.
	 * @see Logger#trace(String, Object...)
	 * @see Logger#isTraceEnabled()
	 * @see #setNotificationAsync(Level, String)
	 */
	public CompletableFuture<String> traceAsync(@Nonnull final Logger logger, @Nonnull final String format, @Nonnull final Object... arguments) {
		return supplyAsync(() -> {
			clearStatusLine();
			logger.trace(format, arguments);
			if(logger.isTraceEnabled()) {
				return setNotification(Level.TRACE, arrayFormat(format, arguments).getMessage(), NOTIFICATION_DEFAULT_DURATION);
			} else {
				return printStatusLine();
			}
		}, getExecutorService());
	}

	/**
	 * Logs a message at {@link Level#DEBUG} using the specified logger, ensuring that the status line is first cleared to prevent any output interference in case
	 * logging is ultimately going to the same destination as the status, and then sets the notification to the same formatted string using the same log level if
	 * the log level is enabled for the logger.
	 * @apiNote This is a convenience method that uses the default notification duration. For more control over logging and notification duration, schedule manual
	 *          logging and notification updates using {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @param logger The logger to use for logging the formatted message.
	 * @param format The string to be formatted.
	 * @param arguments The arguments to the format string.
	 * @return The future updated status line.
	 * @see Logger#debug(String, Object...)
	 * @see Logger#isDebugEnabled()
	 * @see #setNotificationAsync(Level, String)
	 */
	public CompletableFuture<String> debugAsync(@Nonnull final Logger logger, @Nonnull final String format, @Nonnull final Object... arguments) {
		return supplyAsync(() -> {
			clearStatusLine();
			logger.debug(format, arguments);
			if(logger.isDebugEnabled()) {
				return setNotification(Level.DEBUG, arrayFormat(format, arguments).getMessage(), NOTIFICATION_DEFAULT_DURATION);
			} else {
				return printStatusLine();
			}
		}, getExecutorService());
	}

	/**
	 * Logs a message at {@link Level#INFO} using the specified logger, ensuring that the status line is first cleared to prevent any output interference in case
	 * logging is ultimately going to the same destination as the status, and then sets the notification to the same formatted string using the same log level if
	 * the log level is enabled for the logger.
	 * @apiNote This is a convenience method that uses the default notification duration. For more control over logging and notification duration, schedule manual
	 *          logging and notification updates using {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @param logger The logger to use for logging the formatted message.
	 * @param format The string to be formatted.
	 * @param arguments The arguments to the format string.
	 * @return The future updated status line.
	 * @see Logger#info(String, Object...)
	 * @see Logger#isInfoEnabled()
	 * @see #setNotificationAsync(Level, String)
	 */
	public CompletableFuture<String> infoAsync(@Nonnull final Logger logger, @Nonnull final String format, @Nonnull final Object... arguments) {
		return supplyAsync(() -> {
			clearStatusLine();
			logger.info(format, arguments);
			if(logger.isInfoEnabled()) {
				return setNotification(Level.INFO, arrayFormat(format, arguments).getMessage(), NOTIFICATION_DEFAULT_DURATION);
			} else {
				return printStatusLine();
			}
		}, getExecutorService());
	}

	/**
	 * Logs a message at {@link Level#WARN} using the specified logger, ensuring that the status line is first cleared to prevent any output interference in case
	 * logging is ultimately going to the same destination as the status, and then sets the notification to the same formatted string using the same log level if
	 * the log level is enabled for the logger.
	 * @apiNote This is a convenience method that uses the default notification duration. For more control over logging and notification duration, schedule manual
	 *          logging and notification updates using {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @param logger The logger to use for logging the formatted message.
	 * @param format The string to be formatted.
	 * @param arguments The arguments to the format string.
	 * @return The future updated status line.
	 * @see Logger#warn(String, Object...)
	 * @see Logger#isWarnEnabled()
	 * @see #setNotificationAsync(Level, String)
	 */
	public CompletableFuture<String> warnAsync(@Nonnull final Logger logger, @Nonnull final String format, @Nonnull final Object... arguments) {
		return supplyAsync(() -> {
			clearStatusLine();
			logger.warn(format, arguments);
			if(logger.isWarnEnabled()) {
				return setNotification(Level.WARN, arrayFormat(format, arguments).getMessage(), NOTIFICATION_DEFAULT_DURATION);
			} else {
				return printStatusLine();
			}
		}, getExecutorService());
	}

	/**
	 * Logs a message at {@link Level#ERROR} using the specified logger, ensuring that the status line is first cleared to prevent any output interference in case
	 * logging is ultimately going to the same destination as the status, and then sets the notification to the same formatted string using the same log level if
	 * the log level is enabled for the logger.
	 * @apiNote This is a convenience method that uses the default notification duration. For more control over logging and notification duration, schedule manual
	 *          logging and notification updates using {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @param logger The logger to use for logging the formatted message.
	 * @param format The string to be formatted.
	 * @param arguments The arguments to the format string.
	 * @return The future updated status line.
	 * @see Logger#error(String, Object...)
	 * @see Logger#isErrorEnabled()
	 * @see #setNotificationAsync(Level, String)
	 */
	public CompletableFuture<String> errorAsync(@Nonnull final Logger logger, @Nonnull final String format, @Nonnull final Object... arguments) {
		return supplyAsync(() -> {
			clearStatusLine();
			logger.error(format, arguments);
			if(logger.isErrorEnabled()) {
				return setNotification(Level.ERROR, arrayFormat(format, arguments).getMessage(), NOTIFICATION_DEFAULT_DURATION);
			} else {
				return printStatusLine();
			}
		}, getExecutorService());
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
	 * @return The current count of work in progress.
	 * @see #addWork(Object)
	 * @see #removeWork(Object)
	 */
	public int getWorkCount() {
		return workInProgress.size();
	}

	/**
	 * Adds a record of ongoing work. The status will later be updated asynchronously if appropriate. If the work was already recorded as ongoing, no further
	 * action is taken.
	 * @param work The ongoing work to add.
	 * @see #printStatusLineAsync()
	 */
	public void addWork(@Nonnull final W work) {
		if(workInProgress.add(requireNonNull(work))) {
			printStatusLineAsync();
		}
	}

	/**
	 * Removes any record of ongoing work. The status will later be updated asynchronously if appropriate. If the work was not recorded as ongoing, no further
	 * action is taken.
	 * @param work The ongoing work to remove.
	 * @see #printStatusLineAsync()
	 */
	public void removeWork(@Nonnull final W work) {
		if(workInProgress.remove(requireNonNull(work))) {
			printStatusLineAsync();
		}
	}

	/**
	 * Returns a status label representing the current work.
	 * @implSpec The default implementation formats a string containing the count of work in progress and a string label of the work derived by calling
	 *           {@link #toLabel(Object)}.
	 * @param work The work to display in the status.
	 * @return A status string for the work.
	 */
	protected String toStatusLabel(@Nonnull final W work) {
		return "/" + getWorkCount() + ": " + toLabel(work);
	}

	/**
	 * Returns a label to represent the current work with no additional information.
	 * @implSpec The default implementation delegates to {@link Object#toString()}, constrained to a length of {@link #WORK_MAX_LABEL_LENGTH}.
	 * @param work The work to display in the status.
	 * @return A label for the work itself.
	 */
	protected CharSequence toLabel(@Nonnull final W work) {
		return constrainLabelLength(work.toString(), WORK_MAX_LABEL_LENGTH);
	}

	private Optional<String> statusMessage = Optional.empty();

	/** @return The currently set status message, which may not be present. */
	public synchronized Optional<String> findStatusMessage() {
		return statusMessage;
	}

	/**
	 * Asynchronously sets the status message and scheduled the status to be reprinted.
	 * @param statusMessage The status message do be displayed.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> setStatusMessageAsync(@Nonnull final String statusMessage) {
		return supplyAsync(() -> setStatusMessage(statusMessage), getExecutorService());
	}

	/**
	 * Sets the status message and scheduled the status to be reprinted.
	 * @param statusMessage The status message do be displayed.
	 * @return The updated status line.
	 */
	protected synchronized String setStatusMessage(@Nonnull final String statusMessage) {
		this.statusMessage = Optional.of(statusMessage);
		return printStatusLine();
	}

	/**
	 * Asynchronously removes any status message, allowing the work status, if any, to be shown.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> clearStatusMesageAsync() {
		return supplyAsync(this::clearStatusMesage, getExecutorService());
	}

	/**
	 * Removes any status message, allowing the work status, if any, to be shown.
	 * @return The updated status line.
	 */
	protected synchronized String clearStatusMesage() {
		statusMessage = Optional.empty();
		return printStatusLine();
	}

	private Optional<Notification> notification = Optional.empty();

	/**
	 * Determines the current notification in effect. If there was a notification but it has expired, the notification is removed.
	 * @return The currently active notification, if any.
	 */
	public synchronized Optional<Notification> findNotification() {
		if(notification.filter(Notification::isExpired).isPresent()) { //remove the notification if it is expired
			notification = Optional.empty();
		}
		return notification;
	}

	/**
	 * Adds a notification with the default severity and duration. The current notification, if any, will be discarded and replaced with the specified
	 * notification.
	 * @param text The status text to display.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> setNotificationAsync(@Nonnull final String text) {
		return setNotificationAsync(NOTIFICATION_DEFAULT_SEVERITY, text, NOTIFICATION_DEFAULT_DURATION);
	}

	/**
	 * Adds a notification with the default severity. The current notification, if any, will be discarded and replaced with the specified notification.
	 * @param text The status text to display.
	 * @param duration The duration of the notification.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> setNotificationAsync(@Nonnull final String text, @Nonnull final Duration duration) {
		return setNotificationAsync(NOTIFICATION_DEFAULT_SEVERITY, text, duration);
	}

	/**
	 * Adds a notification with the default duration. The current notification, if any, will be discarded and replaced with the specified notification.
	 * @param severity The notification severity (e.g. warning or error).
	 * @param text The status text to display.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> setNotificationAsync(@Nonnull final Level severity, @Nonnull final String text) {
		return setNotificationAsync(severity, text, NOTIFICATION_DEFAULT_DURATION);
	}

	/**
	 * Adds a notification asynchronously. The current notification, if any, will be discarded and replaced with the specified notification.
	 * @param severity The notification severity (e.g. warning or error).
	 * @param text The status text to display.
	 * @param duration The duration of the notification.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> setNotificationAsync(@Nonnull final Level severity, @Nonnull final String text, @Nonnull final Duration duration) {
		return supplyAsync(() -> setNotification(severity, text, duration), getExecutorService());
	}

	/**
	 * Adds a notification. The current notification, if any, will be discarded and replaced with the specified notification.
	 * @param severity The notification severity (e.g. warning or error).
	 * @param text The status text to display.
	 * @param duration The duration of the notification.
	 * @return The updated status line.
	 */
	protected synchronized String setNotification(@Nonnull final Level severity, @Nonnull final String text, @Nonnull final Duration duration) {
		notification = Optional.of(new Notification(severity, text, duration));
		return printStatusLine();
	}

	/**
	 * Asynchronously removes any notification even if not yet expired, allowing the status message or work status, if any, to be shown.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> clearNotificationAsync() {
		return supplyAsync(this::clearNotification, getExecutorService());
	}

	/**
	 * Removes any notification even if not yet expired, allowing the status message or work status, if any, to be shown.
	 * @return The updated status line.
	 */
	protected synchronized String clearNotification() {
		notification = Optional.empty();
		return printStatusLine();
	}

	/**
	 * Retrieves the current status label to display.
	 * <p>
	 * There are three sources of the status label, in order of priority:
	 * </p>
	 * <ul>
	 * <li>A <dfn>notification</dfn> returned by {@link #findNotification()}, a temporary change in status message that only last for a certain amount of time.
	 * <li>A manually-set status <dfn>message</dfn> returned by {@link #findStatusMessage()}. If set, the status message never goes away until cleared, unless it
	 * is temporarily superseded by a notification.
	 * <li>An indication of current work, if any.</li>
	 * </ul>
	 * @implSpec This implementation shows any notification using rich ANSI text if appropriate based upon its severity.
	 * @return The current status string to show, which may not be present if no message or notification is set, and no work is in progress.
	 */
	public synchronized Optional<String> findStatusLabel() {
		return findNotification().map(Notification::getAnsiText).or(this::findStatusMessage).or(() -> findStatusWork().map(this::toStatusLabel));
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) a new line to appear above the status, to the same output as the status, scrolling previous
	 * information up.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @implSpec This method delegates to {@link #printLines(Iterable)}.
	 * @param line The line to print.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> printLineAsync(@Nonnull final CharSequence line) {
		return printLinesAsync(singleton(line));
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) new lines to appear above the status, to the same output as the status, scrolling previous
	 * information up. The lines are guaranteed to be grouped together.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @implSpec This method delegates to {@link #printLines(Iterable)}.
	 * @param lines The lines to print.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> printLinesAsync(@Nonnull final CharSequence... lines) {
		return printLinesAsync(List.of(lines));
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) new lines to appear above the status, to the same output as the status, scrolling previous
	 * information up. The lines are guaranteed to be grouped together.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @param lines The lines to print.
	 * @return The future updated status line.
	 */
	public CompletableFuture<String> printLinesAsync(@Nonnull final Iterable<? extends CharSequence> lines) {
		return supplyAsync(() -> printLines(lines), getExecutorService());
	}

	/**
	 * Prints zero, one, or several new lines to appear above the status, to the same output as the status, scrolling previous information up. The lines are
	 * guaranteed to be grouped together.
	 * @apiNote This is useful for providing some useful information in addition to the status line.
	 * @apiNote Normally applications and subclasses will not call this method directly. Instead they should schedule the printing later using
	 *          {@link #printLineAsync(String)}.
	 * @implSpec The implementation prints each line over the current status, skips to the next line, and prints the current status. This has the effect of
	 *           scrolling information up and printing a line above the status.
	 * @param lines The lines to print.
	 * @return The updated status line.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 * @see #printStatusLine()
	 */
	protected synchronized String printLines(@Nonnull final Iterable<? extends CharSequence> lines) {
		final Iterator<? extends CharSequence> lineIterator = lines.iterator();
		if(!lineIterator.hasNext()) {
			return lastStatusLine; //nothing to do
		}
		while(lineIterator.hasNext()) {
			final CharSequence line = lineIterator.next();
			final int padWidth = lastStatusLine != null ? lastStatusLine.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
			try {
				getOut().append(format("\r%-" + padWidth + "s%n", line));
				lastStatusLine = null; //we're skipping to another line for further status
			} catch(final IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
		}
		return printStatusLine();
	}

	/**
	 * Asynchronously clears the current status line by blanking out the status and returning the cursor to the beginning of the line. The cursor does not move to
	 * the next line.
	 * @apiNote This is one way to ensure that new information is printed serially after the status line is cleared. Chaining {@link CompletableFuture} allows
	 *          subsequent operations to occur, such as printing the status line again via {@link #printStatusLine()}. If chaining {@link CompletableFuture}, be
	 *          sure and specify this instance as the {@link Executor}. Another simpler approach would be to schedule the entire operation using
	 *          {@link #supplyWithoutStatusLineAsync(Runnable)}.
	 * @return The updated status line, which will be the empty string.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 */
	public CompletableFuture<String> clearStatusLineAsync() {
		return supplyAsync(() -> {
			clearStatusLine();
			return "";
		}, getExecutorService());
	}

	/**
	 * Clears the current status line by blanking out the status and returning the cursor to the beginning of the line. The cursor does not move to the next line.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending information.
	 */
	protected synchronized void clearStatusLine() {
		final int padWidth = lastStatusLine != null ? lastStatusLine.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
		try {
			getOut().append(format("\r%-" + padWidth + "s\r", ""));
		} catch(final IOException ioException) {
			throw new UncheckedIOException(ioException);
		}
		lastStatusLine = null;
	}

	/**
	 * Asynchronously prints (i.e. schedules for printing later) the current status, including the elapsed time, count, and current status file.
	 * @return The future updated status line.
	 * @see #printStatusLine()
	 */
	public CompletableFuture<String> printStatusLineAsync() {
		return supplyAsync(this::printStatusLine, getExecutorService());
	}

	/** Keeps track of the entire last status string to prevent unnecessary re-printing and to determine padding. */
	@Nullable
	private String lastStatusLine = null;

	/**
	 * Prints the current status, including the elapsed time, optional count, and label.
	 * @apiNote Normally applications and subclasses will not call this method directly. Instead they should schedule the status printing later using
	 *          {@link #printStatusLineAsync()}.
	 * @implSpec if {@link #getOut()} is an implementation of {@link Flushable}, the output is flushed.
	 * @throws UncheckedIOException if {@link #getOut()} throws an {@link IOException} when appending or flushing information.
	 * @return The new status line.
	 * @see #getElapsedTime()
	 * @see #getCounter()
	 * @see #findStatusLabel()
	 */
	protected synchronized String printStatusLine() {
		final StringBuilder statusLineStringBuilder = new StringBuilder();
		//elapsed time
		final Duration elapsedTime = getElapsedTime();
		statusLineStringBuilder.append(format("%d:%02d:%02d", elapsedTime.toHours(), elapsedTime.toMinutesPart(), elapsedTime.toSecondsPart()));
		//count
		final long count = getCounter().get();
		if(count >= 0) {
			statusLineStringBuilder.append(" | ").append(count);
			final long total = getTotal();
			if(total >= 0) {
				statusLineStringBuilder.append('/').append(total);
			}
		}
		//status label
		findStatusLabel().ifPresent(label -> statusLineStringBuilder.append(" | ").append(label));
		final String statusLine = statusLineStringBuilder.toString();
		if(!statusLine.equals(lastStatusLine)) { //if the status is different than the last time (or there was no previous status)
			//We only have to pad to the last actual status, _not_ to the _padded_ last status, because
			//if the last status was printed padded, it would have erased the previous status already.
			//In other words, padding only needs to be added once to overwrite each previous status.
			final int padWidth = lastStatusLine != null ? lastStatusLine.length() : 1; //pad at least to a nonzero value to avoid a MissingFormatWidthException
			try {
				final Appendable out = getOut();
				out.append(format("\r%-" + padWidth + "s", statusLine));
				if(out instanceof Flushable) {
					((Flushable)out).flush();
				}
			} catch(final IOException ioException) {
				throw new UncheckedIOException(ioException);
			}
			lastStatusLine = statusLine; //update the last status for checking the next time
		}
		return statusLine;
	}

	/**
	 * {@inheritDoc}
	 * @implSpec This implementation clears the status line and then shuts down the executor used for printing the status.
	 * @throws IOException If the status print executor could not be shut down.
	 */
	@Override
	public void close() throws IOException {
		executorService.execute(this::clearStatusLine);
		executorService.shutdown();
		try {
			if(!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
				if(!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
					throw new IOException("Status printing service not shut down properly.");
				}
			}
		} catch(final InterruptedException interruptedException) {
			executorService.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Constrains a string length by cutting it in the middle if it is too long and inserting an ellipsis in the gap as a single character replacement.
	 * @param charSequence The label to constrain.
	 * @param maxLength The maximum length to constrain.
	 * @return The label constrained to a certain length.
	 */
	protected static CharSequence constrainLabelLength(@Nonnull final CharSequence charSequence, @Nonnegative final int maxLength) { //TODO transfer to a library utility method; add tests; create improved version that understands path segments
		checkArgumentNotNegative(maxLength);
		if(maxLength == 0) {
			return "";
		}
		if(maxLength == 1) {
			return "…";
		}
		final int length = charSequence.length();
		if(length <= maxLength) {
			return charSequence;
		}
		final int cutLength = length - maxLength;
		assert cutLength >= 0;
		final int cutStart = maxLength / 2;
		return new StringBuilder().append(charSequence, 0, cutStart).append('…').append(charSequence, cutStart + cutLength - 1, maxLength); //compensate for the character we're adding
	}

	/**
	 * A temporary status change that is shown for a certain amount of time.
	 * @author Garret Wilson
	 */
	private static class Notification {

		private final Level severity;

		/** @return The notification severity (e.g. warning or error). */
		public Level getSeverity() {
			return severity;
		}

		private final String text;

		/** @return The status text to display. */
		public String getText() {
			return text;
		}

		/**
		 * @return The status text to display in rich text using ANSI escape codes based on the severity.
		 * @see #getText()
		 * @see #getSeverity()
		 * @see #findColor(Level)
		 */
		public String getAnsiText() {
			final String text = getText();
			return findColor(getSeverity()).map(color -> ansi().bold().fg(color).a(text).reset().toString()).orElse(text);
		}

		private final long endTimeNs;

		/** @return The ending time, exclusive, of the notification in terms of {@link System#nanoTime()} . */
		public long getEndTimNs() {
			return endTimeNs;
		}

		/**
		 * Determines whether the notification is currently expired based upon the current {@link System#nanoTime()}.
		 * @return Whether the notification has expired.
		 * @see #getEndTimNs()
		 */
		public boolean isExpired() {
			return System.nanoTime() >= getEndTimNs();
		}

		/**
		 * Constructor
		 * @param severity The notification severity (e.g. warning or error).
		 * @param text The status text to display.
		 * @param duration The duration of the notification.
		 */
		private Notification(@Nonnull final Level severity, @Nonnull final String text, @Nonnull final Duration duration) {
			this.severity = requireNonNull(severity);
			this.text = requireNonNull(text);
			this.endTimeNs = System.nanoTime() + duration.toNanos();
		}

		/**
		 * Finds the appropriate ANSI color to use for the given severity.
		 * @param severity The notification severity.
		 * @return The ANSI color for the severity, if any.
		 */
		public static Optional<Color> findColor(@Nonnull final Level severity) {
			switch(severity) {
				case ERROR:
					return Optional.of(Color.RED);
				case WARN:
					return Optional.of(Color.YELLOW);
				case INFO:
					return Optional.of(Color.CYAN);
				default:
					return Optional.empty();
			}

		}

	}

}
