/*
 * Jogger is a lightweight, minimal dependency JSON logging utility for java.</br>
 * This work is shared under the Creative Commons Attribution-ShareAlike 4.0 International Public License
 * @author Walker Case
 */
package Jogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Used to log information to the disk.
 * 
 * @author Walker Case
 */
public class Jogger {

	/**
	 * The level to log the provided information with.
	 */
	public enum LogLevel {

		/** The info. */
		INFO,
		/** The debug. */
		DEBUG,
		/** The warn. */
		WARN,
		/** The error. */
		ERROR;
	}

		private static FileWriter writer;
	
	/**
	 * Custom output stream.
	 */
	private static OutputStream outputStream = null;
	
	/**
	 * Disables logging to a file if true.
	 */
	private static boolean disableFileLogging = false;

	/** The log file. */
	private static File logFile;

	/** The last flush. */
	private static long lastFlush = 0;

	/** The queue. */
	private static ArrayList<String> queue = new ArrayList<String>();

	/*
	 * Load the log file on startup.
	 */
	static {
		generateLog();
	}

	/**
	 * This method is run on startup or any time a new log needs to be generated.
	 */
	private static void generateLog() {
		try {
			removeOldLogs(60, 5000);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			File lf = new File("logs/");
			if (!lf.exists()) {
				lf.mkdirs();
			}

			String datedName = new SimpleDateFormat("MM_dd_yyyy_hh_mm_ss_SSSS").format(new Date());

			logFile = new File("logs/" + "log" + "_" + datedName + ".log");
			if (!logFile.exists()) {
				logFile.createNewFile();
			}

			writer = new FileWriter(logFile, true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Redirects output to the given OutputStream.
	 * @param OutputStream os
	 * @param boolean disableFileLogging
	 */
	public static void redirectOutput(OutputStream os, boolean disableFileLoggingg) {
		synchronized(outputStream) {
			outputStream = os;
		}
		disableFileLogging = disableFileLoggingg;
	}

	/**
	 * Format and log the given JSONArray.
	 *
	 * @param security the security
	 * @param bytes    the bytes
	 */
	public static void log(LogLevel security, byte[] bytes) {
		log(security, new JSONArray(bytes));
	}

	/**
	 * Format and log the given JSONObject.
	 *
	 * @param security   the security
	 * @param jsonObject the json object
	 */
	public static void log(LogLevel security, JSONObject jsonObject) {
		log(security, jsonObject.toString());
	}

	/**
	 * Format and log the given JSONArray.
	 *
	 * @param security  the security
	 * @param jsonArray the json array
	 */
	public static void log(LogLevel security, JSONArray jsonArray) {
		log(security, jsonArray.toString());
	}

	/**
	 * Format and log the given text.
	 *
	 * @param severity the severity
	 * @param message  the message
	 */
	public static void log(LogLevel severity, String message) {
		if (logFile == null)
			generateLog();
		String formatted = String.format("[%s] [%s] (%s) %s", severity,
				Thread.currentThread().getStackTrace()[2].getClassName(), System.currentTimeMillis(), message);

		if (severity == LogLevel.ERROR) {
			System.err.println(formatted);
		} else {
			System.out.println(formatted);
		}

		// Create a JSONObject to be printed to the file.
		// This is done to limit the amount of information printed to the console.
		JSONObject toFile = new JSONObject();
		toFile.put("message", message);
		toFile.put("severity", severity);

		toFile.put("date", new SimpleDateFormat("MM_dd_yyyy").format(new Date()));
		toFile.put("systemtime", System.currentTimeMillis());

		JSONArray stackTrace = new JSONArray();
		ArrayList<String> lastCallers = new ArrayList<String>();
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		for (StackTraceElement st : stackTraceElements) {
			if (!st.getClassName().startsWith("java.lang.Thread"))
				stackTrace.put(st);
		}
		toFile.put("stack", stackTrace);

		// Immediate cleanup.
		stackTraceElements = null;
		lastCallers.clear();
		lastCallers = null;

		synchronized (queue) {
			queue.add(toFile.toString());
		}
		// print(formatted);
		// Save messages in 3 second intervals.
		if (lastFlush + 3000 <= System.currentTimeMillis()) {
			flush();
			lastFlush = System.currentTimeMillis();
		}
	}

	/**
	 * Flushes the queue to the disk.
	 */
	public static void flush() {
		synchronized (queue) {
			for (String s : queue) {
				print(new String(s));
			}
			queue.clear();
		}
	}

	/**
	 * Log the given exception.<br>
	 * This bypasses the queue and immediately flushes the information to the log
	 * file.
	 *
	 * @param e the e
	 * @return Throwable - The exception that was passed.
	 */
	public static Throwable logException(Throwable e) {
		if (logFile == null)
			generateLog();
		if (e == null)
			return null;
		JSONObject toFile = new JSONObject();
		JSONArray stackTrace = new JSONArray();

		toFile.put("severity", LogLevel.ERROR);
		toFile.put("date", new SimpleDateFormat("MM_dd_yyyy").format(new Date()));
		toFile.put("systemtime", System.currentTimeMillis());
		toFile.put("message", e.getMessage() != null ? e.getMessage() : "null");
		toFile.put("callingClass", Thread.currentThread().getStackTrace()[0].getClassName());

		String stString = "";
		ArrayList<String> lastCallers = new ArrayList<String>();
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		for (StackTraceElement st : stackTraceElements) {
			if (!st.getClassName().startsWith("java.lang.Thread")) {
				stackTrace.put(st);
				if (lastCallers.contains(st.getLineNumber() + ":" + st.getClassName() + "#" + st.getMethodName()))
					continue;
				lastCallers.add(st.getLineNumber() + ":" + st.getClassName() + "#" + st.getMethodName());
				stString += "," + st.getClassName() + "#" + st.getMethodName() + ":" + st.getLineNumber();
			}
		}
		toFile.put("stack", stackTrace);

		String formatted = String.format("[%s] [%s] [%d] Found Error: %s\n%s", LogLevel.ERROR,
				Thread.currentThread().getStackTrace()[2].getClassName(), System.currentTimeMillis(), e.getMessage(),
				stString);
		System.err.println(formatted);
		e.printStackTrace();

		// Create a JSONObject to be printed to the file.
		// This is done to limit the amount of information printed to the console.

		// Immediate cleanup.
		stackTraceElements = null;
		lastCallers.clear();
		lastCallers = null;

		// Immediately flush the queue to help ensure error is captured.
		synchronized (queue) {
			queue.add(toFile.toString());
			flush();
		}
		return e;
	}

	/**
	 * Print the string into the log file without formatting.
	 *
	 * @param message the message
	 */
	private static void print(String message) {
		if(outputStream != null) {
			synchronized(outputStream) {
				try {
					outputStream.write(message.getBytes());
					outputStream.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		try {
			if (writer == null || disableFileLogging)
				return;
			writer.write(message + "\n");
			writer.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compresses the contents of any non-compressed logs and closes the associated
	 * loggers (if they still exist).
	 *
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void compressLogs() throws IOException {
		flush();

		// If the log file is null there isn't anything to compress at this point so we
		// skip it.
		if (logFile == null)
			return;

		if (logFile.toString().endsWith(".log")) {
			byte[] buffer = new byte[1024];
			File out = new File(logFile.toString().replace(".log", ".clog"));

			// Compress the log file.
			try {
				GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(out));
				FileInputStream fis = new FileInputStream(logFile);

				int len;
				while ((len = fis.read(buffer)) > 0) {
					gzip.write(buffer, 0, len);
				}

				fis.close();
				gzip.finish();
				gzip.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Close the logger and set the variables to null.
			writer.close();
			logFile.delete();
			logFile = null;
			writer = null;
		}
	}

	/**
	 * Remove old logs from x number of days</br>
	 * Or of a file size greater than x MB.
	 *
	 * @param days   the days
	 * @param mBytes the m bytes
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void removeOldLogs(int days, int mBytes) throws IOException {
		Stream<Path> pathStream = Files.list(new File("logs/").toPath());
		pathStream.forEach(new Consumer<Path>() {
			@Override
			public void accept(Path t) {
				long diff = new Date().getTime() - t.toFile().lastModified();

				if (diff > days * 24 * 60 * 60 * 1000 || (t.toFile().length() / 2048 > mBytes)) {
					t.toFile().delete();
				}
			}
		});
		pathStream.close();
	}

	/**
	 * Returns the underlying {@link java.io.File} element for the logger.
	 *
	 * @return the log file
	 */
	public static File getLogFile() {
		return logFile;
	}

}