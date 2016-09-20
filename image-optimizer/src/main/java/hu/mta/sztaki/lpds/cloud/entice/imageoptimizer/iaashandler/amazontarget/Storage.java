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
package hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.iaashandler.amazontarget;

import hu.mta.sztaki.lpds.cloud.entice.imageoptimizer.Shrinker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

public class Storage {
	private static final int BUFFER_SIZE = 1024;
	private static final int MAX_CONNECTIONS = 10;

	/**
	 * @param endpoint S3 endpoint URL
	 * @param accessKey Access key
	 * @param secretKey Secret key
	 * @param bucket Bucket name 
	 * @param path Key name of the object to download (path + file name)
	 * @param file Local file to download to 
	 * @throws Exception On any error
	 */
	public static void download(String endpoint, String accessKey, String secretKey, String bucket, String path, File file) throws Exception {
		AmazonS3Client amazonS3Client = null;
		InputStream in = null;
		OutputStream out = null; 
		try {
			AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
			ClientConfiguration clientConfiguration = new ClientConfiguration();
			clientConfiguration.setMaxConnections(MAX_CONNECTIONS); 
			clientConfiguration.setMaxErrorRetry(PredefinedRetryPolicies.DEFAULT_MAX_ERROR_RETRY);
			clientConfiguration.setConnectionTimeout(ClientConfiguration.DEFAULT_CONNECTION_TIMEOUT); 
			amazonS3Client = new AmazonS3Client(awsCredentials, clientConfiguration);
			S3ClientOptions clientOptions = new S3ClientOptions().withPathStyleAccess(true);
			amazonS3Client.setS3ClientOptions(clientOptions);
			amazonS3Client.setEndpoint(endpoint);
			S3Object object = amazonS3Client.getObject(new GetObjectRequest(bucket, path));
			in = object.getObjectContent();
			byte[] buf = new byte[BUFFER_SIZE];
			out = new FileOutputStream(file);
			int count;
			while( (count = in.read(buf)) != -1) out.write(buf, 0, count);
			out.close();
			in.close();
		} catch (AmazonServiceException x) {
			Shrinker.myLogger.info("download error: " + x.getMessage());
			throw new Exception("download exception", x);
		} catch (AmazonClientException x) {
			Shrinker.myLogger.info("download error: " + x.getMessage());
			throw new Exception("download exception", x);
		} catch (IOException x) {
			Shrinker.myLogger.info("download error: " + x.getMessage());
			throw new Exception("download exception", x);
		}
		finally {
			if (in != null) { try { in.close(); } catch(Exception e) {} }
			if (out != null) { try { out.close(); } catch(Exception e) {} }
			if (amazonS3Client != null) { try { amazonS3Client.shutdown(); } catch(Exception e) {} }
		}
	}

	/**
	 * @param file Local file to upload
	 * @param endpoint S3 endpoint URL
	 * @param accessKey Access key
	 * @param secretKey Secret key
	 * @param bucket Bucket name 
	 * @param path Key name (path + file name)
	 * @throws Exception On any error
	 */
	public static void upload(File file, String endpoint, String accessKey, String secretKey, String bucket, String path) throws Exception {
		AmazonS3Client amazonS3Client = null;
		try {
			AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
			ClientConfiguration clientConfiguration = new ClientConfiguration(); 
			clientConfiguration.setMaxConnections(MAX_CONNECTIONS); 
			clientConfiguration.setMaxErrorRetry(PredefinedRetryPolicies.DEFAULT_MAX_ERROR_RETRY);
			clientConfiguration.setConnectionTimeout(ClientConfiguration.DEFAULT_CONNECTION_TIMEOUT); 
			amazonS3Client = new AmazonS3Client(awsCredentials, clientConfiguration);
			S3ClientOptions clientOptions = new S3ClientOptions().withPathStyleAccess(true);
			amazonS3Client.setS3ClientOptions(clientOptions);
			amazonS3Client.setEndpoint(endpoint);
//			amazonS3Client.putObject(new PutObjectRequest(bucket, path, file)); // up to 5GB
			TransferManager tm = new TransferManager(amazonS3Client); // up to 5TB
			Upload upload = tm.upload(bucket, path, file);
			// while (!upload.isDone()) { upload.getProgress().getBytesTransferred(); Thread.sleep(1000); } // to get progress
			upload.waitForCompletion();
			tm.shutdownNow();
		} 
		catch (AmazonServiceException x) {
			Shrinker.myLogger.info("upload error: " + x.getMessage());
			throw new Exception("upload exception", x);
		} 
		catch (AmazonClientException x) {
			Shrinker.myLogger.info("upload error: " + x.getMessage());
			throw new Exception("upload exception", x);
		}
		finally {
			if (amazonS3Client != null) { try { amazonS3Client.shutdown(); } catch(Exception e) {} }
		}
	}
	
	public static void main(String [] args) throws Exception {
		String accessKey = "ahajnal"; // NOTE: without @sztaki.hu
		String secretKey = "2ca...";
		String endpoint = "s3.lpds.sztaki.hu";
		String bucket = "s3bucket";
		String fileToUpload = "c:/LPDS/Entice/images/Wordpress_32_bit_2.6rev2_flexiantkvm.qcow2";
		String pathToUploadTo = "wordpress.qcow2"; // /bucket/pathToUploadTo
		String pathToDownloadFrom = "1MB.dat"; // // /bucket/pathToDownloadFrom
		String fileDownLoaded = "c:/Trash/safetodelete";
		upload(new File(fileToUpload) , endpoint, accessKey, secretKey, bucket, pathToUploadTo);
		download(endpoint, accessKey, secretKey, bucket, pathToDownloadFrom, new File(fileDownLoaded));
	}
}