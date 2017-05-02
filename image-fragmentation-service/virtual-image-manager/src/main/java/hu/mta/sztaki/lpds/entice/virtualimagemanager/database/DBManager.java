package hu.mta.sztaki.lpds.entice.virtualimagemanager.database;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBManager {
	
	private static final Logger log = LoggerFactory.getLogger(DBManager.class);
	private static final String PERSISTENCE_UNIT_NAME = "hu.mta.sztaki.lpds.entice.virtualimagemanager.jpa";
	private EntityManagerFactory entityManagerFactory = null;
	private static final DBManager INSTANCE = new DBManager(); // singleton

	public static DBManager getInstance() { return INSTANCE; }
	
	private DBManager() {
		log.debug("Testing database connection...");
		try { 
			EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
			log.debug("Creating entity manager...");
			EntityManager em = emf.createEntityManager();
			log.debug("Testing transaction...");
			em.getTransaction().begin(); // test connection
			em.getTransaction().commit();
			em.close();
			log.info("Database connection established!");
			this.entityManagerFactory = emf;
		} catch (Throwable e) {
			System.out.println("Could not open database connection!");
			System.out.flush();
			e.printStackTrace();
			System.out.flush();
			log.error("Could not open database connection (" + PERSISTENCE_UNIT_NAME + "): " + (entityManagerFactory!=null?entityManagerFactory.getProperties():"?"), e);
		}
	}

	void shutdown() {
		if (entityManagerFactory != null) { // close entity manager factory
			log.debug("Closing entity manager factory...");
			try { entityManagerFactory.close();	} 
			catch (Throwable e) { log.warn("Cannot close entity manager factory!", e); }
		}
	}
	
	EntityManagerFactory getEntityManagerFactory() {
		if (entityManagerFactory == null) log.error("No database connection!"); 
		return entityManagerFactory;
	}
	
	public EntityManager getEntityManager() throws Exception {
		EntityManager em = null;
		if (entityManagerFactory == null) {
			log.error("No entity manager factory!"); 
		} else {
			try { em = entityManagerFactory.createEntityManager(); } 
			catch (IllegalStateException e) { log.error("Cannot create entity manager!", e); }
		}
		if (em == null) throw new Exception("Cannot connect to database!");
		else return em;
	}
}