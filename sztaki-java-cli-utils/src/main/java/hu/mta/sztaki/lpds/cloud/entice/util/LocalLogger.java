/*
 *    Copyright 2009-2015 Gabor Kecskemeti, University of Westminster, MTA SZTAKI
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.mta.sztaki.lpds.cloud.entice.util;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LocalLogger {
	public static Logger myLogger = Logger.getLogger("SZTAKI.Scripts");
	static {
		try {
			myLogger.setLevel(Level.ALL);
			myLogger.setUseParentHandlers(false);
			FileHandler handler = new FileHandler("ENTICE-Utils.log", false);
			handler.setFormatter(new Formatter() {
				@Override
				public String format(LogRecord record) {
					StringBuilder sb = new StringBuilder(record.getLevel()
							.getName());
					sb.append(" ");
					sb.append(record.getMillis());
					sb.append(" T");
					sb.append(record.getThreadID());
					sb.append(" ");
					sb.append(record.getSourceClassName());
					sb.append(".");
					sb.append(record.getSourceMethodName());
					sb.append("(): ");
					sb.append(record.getMessage());
					sb.append("\n");
					return sb.toString();
				}
			});
			myLogger.addHandler(handler);
		} catch (IOException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
