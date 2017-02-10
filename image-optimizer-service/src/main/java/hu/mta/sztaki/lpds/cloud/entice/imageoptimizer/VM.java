package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer;

import java.util.Map;

/*
 *    Copyright 2016 Akos Hajnal, MTA SZTAKI
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

public abstract class VM {
	public static final String UNKNOWN = "unknown";
	public static final String PENDING = "pending";
	public static final String BOOTING = "booting";
	public static final String RUNNING = "running";
	public static final String SHUTDOWN = "shutting-down";
	public static final String STOPPING = "stopping";
	public static final String STOPPED = "stopped";
	public static final String TERMINATED = "terminated";
	public static final String ERROR = "error";

	public static final String USER_DATA_BASE64 = "userDataBase64";
	public static final String LOGIN = "login";
	public static final String SSH_KEY_PATH = "sshKeyPath";

	// launch VM
	public abstract void run(Map<String,String> parameters) throws Exception;
	// get instance id
	public abstract String getInstanceId();
	// update VM details such as status, IP 
	public abstract void describeInstance() throws Exception;
	// get private VM status
	public abstract String getStatus();
	// get private IP
	public abstract String getIP();
	// terminate VM
	public abstract void terminate() throws Exception;
	// reboot VM
	public abstract void reboot() throws Exception;
	// release resources
	public abstract void discard();
}