package com.github.mongobee;

import static com.mongodb.ServerAddress.defaultHost;
import static com.mongodb.ServerAddress.defaultPort;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.jongo.Jongo;
import org.osgi.framework.BundleContext;

import com.github.mongobee.changeset.ChangeEntry;
import com.github.mongobee.dao.ChangeEntryDao;
import com.github.mongobee.exception.MongobeeChangeSetException;
import com.github.mongobee.exception.MongobeeConfigurationException;
import com.github.mongobee.exception.MongobeeException;
import com.github.mongobee.utils.ChangeService;
import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClientURI;

/**
 * Mongobee runner
 *
 * @author lstolowski
 * @since 26/07/2014
 */
public class Mongobee {
	private static final Logger LOGGER = Logger.getLogger(Mongobee.class.getName());

	private ChangeEntryDao dao;

	private boolean enabled = true;
	private String changeLogsScanPackage;
	private MongoClientURI mongoClientURI;
	private Mongo mongo;
	private String dbName;
	private BundleContext bundleContext;
	
	/**
	 * <p>
	 * Simple constructor with default configuration of host (localhost) and
	 * port (27017). Although <b>the database name need to be provided</b> using
	 * {@link Mongobee#setDbName(String)} setter.
	 * </p>
	 * <p>
	 * It is recommended to use constructors with MongoURI
	 * </p>
	 */
	public Mongobee() {
		this(new MongoClientURI("mongodb://" + defaultHost() + ":"
				+ defaultPort() + "/"));
	}

	/**
	 * <p>
	 * Constructor takes db.mongodb.MongoClientURI object as a parameter.
	 * </p>
	 * <p>
	 * For more details about MongoClientURI please see
	 * com.mongodb.MongoClientURI docs
	 * </p>
	 *
	 * @param mongoClientURI
	 *            uri to your db
	 * @see MongoClientURI
	 */
	public Mongobee(MongoClientURI mongoClientURI) {
		this.mongoClientURI = mongoClientURI;
		this.setDbName(mongoClientURI.getDatabase());
		this.dao = new ChangeEntryDao();
	}

	/**
	 * <p>
	 * Constructor takes db.mongodb.Mongo object as a parameter.
	 * </p>
	 * <p>
	 * For more details about <tt>Mongo</tt> please see com.mongodb.Mongo docs
	 * </p>
	 *
	 * @param mongo
	 *            database connection
	 * @see Mongo
	 */
	public Mongobee(Mongo mongo) {
		this.mongo = mongo;
		this.dao = new ChangeEntryDao();
	}

	/**
	 * <p>
	 * Mongobee runner. Correct MongoDB URI should be provided.
	 * </p>
	 * <p>
	 * The format of the URI is:
	 * 
	 * <pre>
	 *   mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database[.collection]][?options]]
	 * </pre>
	 * 
	 * <ul>
	 * <li>{@code mongodb://} Required prefix</li>
	 * <li>{@code username:password@} are optional. If given, the driver will
	 * attempt to login to a database after connecting to a database server. For
	 * some authentication mechanisms, only the username is specified and the
	 * password is not, in which case the ":" after the username is left off as
	 * well.</li>
	 * <li>{@code host1} Required. It identifies a server address to connect to.
	 * More than one host can be provided.</li>
	 * <li>{@code :portX} is optional and defaults to :27017 if not provided.</li>
	 * <li>{@code /database} the name of the database to login to and thus is
	 * only relevant if the {@code username:password@} syntax is used. If not
	 * specified the "admin" database will be used by default. <b>Mongobee will
	 * operate on the database provided here or on the database overriden by
	 * setter setDbName(String).</b></li>
	 *
	 * <li>{@code ?options} are connection options. For list of options please
	 * see com.mongodb.MongoClientURI docs</li>
	 * </ul>
	 *
	 * <p>
	 * For details, please see com.mongodb.MongoClientURI
	 *
	 * @param mongoURI
	 *            with correct format
	 * @see com.mongodb.MongoClientURI
	 */

	public Mongobee(String mongoURI) {
		this(new MongoClientURI(mongoURI));
	}

	/**
	 * Executing migration
	 *
	 * @throws MongobeeException
	 *             exception
	 */
	public void execute() throws MongobeeException {
		if (!isEnabled()) {
			LOGGER.log(Level.INFO, "Mongobee is disabled. Exiting.");
			return;
		}

		validateConfig();

		LOGGER.log(Level.INFO,
				"Mongobee has started the data migration sequence..");
		if (this.mongo != null) {
			dao.connectMongoDb(this.mongo, dbName);
		} else {
			dao.connectMongoDb(this.mongoClientURI, dbName);
		}

		ChangeService service = new ChangeService(changeLogsScanPackage, bundleContext);

		for (Class<?> changelogClass : service.fetchChangeLogs()) {
			LOGGER.log(Level.FINE, "Examining class {0}.", changelogClass);
			Object changelogInstance = null;
			try {
				changelogInstance = changelogClass.getConstructor()
						.newInstance();
				List<Method> changesetMethods = service
						.fetchChangeSets(changelogInstance.getClass());

				for (Method changesetMethod : changesetMethods) {
					ChangeEntry changeEntry = service
							.createChangeEntry(changesetMethod);

					try {
						if (dao.isNewChange(changeEntry)) {
							executeChangeSetMethod(changesetMethod,
									changelogInstance, dao.getDb());
							dao.save(changeEntry);
							LOGGER.log(Level.FINE, "{0} applied.", changeEntry);
						} else if (service
								.isRunAlwaysChangeSet(changesetMethod)) {
							executeChangeSetMethod(changesetMethod,
									changelogInstance, dao.getDb());
							LOGGER.log(Level.FINE, "{0} reapplied.", changeEntry);
						} else {
							LOGGER.log(Level.FINE, "{0} passed over.", changeEntry);
						}
					} catch (MongobeeChangeSetException e) {
						LOGGER.log(Level.SEVERE, e.getMessage());
					}
				}
			} catch (NoSuchMethodException e) {
				throw new MongobeeException(e.getMessage());
			} catch (IllegalAccessException e) {
				throw new MongobeeException(e.getMessage());
			} catch (InvocationTargetException e) {
				throw new MongobeeException(e.getMessage());
			} catch (InstantiationException e) {
				throw new MongobeeException(e.getMessage());
			}

		}
		LOGGER.log(Level.INFO, "Mongobee has finished his job.");
	}

	private Object executeChangeSetMethod(Method changeSetMethod,
			Object changeLogInstance, DB db) throws IllegalAccessException,
			InvocationTargetException, MongobeeChangeSetException {
		if (changeSetMethod.getParameterTypes().length == 1
				&& changeSetMethod.getParameterTypes()[0].equals(DB.class)) {
			LOGGER.log(Level.FINE, "method with DB argument");

			return changeSetMethod.invoke(changeLogInstance, db);
		} else if (changeSetMethod.getParameterTypes().length == 1
				&& changeSetMethod.getParameterTypes()[0].equals(Jongo.class)) {
			LOGGER.log(Level.FINE, "method with Jongo argument");

			return changeSetMethod.invoke(changeLogInstance, new Jongo(db));
		} else if (changeSetMethod.getParameterTypes().length == 0) {
			LOGGER.log(Level.FINE, "method with no params");

			return changeSetMethod.invoke(changeLogInstance);
		} else {
			throw new MongobeeChangeSetException(
					"ChangeSet method "
							+ changeSetMethod.getName()
							+ " has wrong arguments list. Please see docs for more info!");
		}
	}

	private void validateConfig() throws MongobeeConfigurationException {
		if (StringUtils.isEmpty(dbName)) {
			throw new MongobeeConfigurationException(
					"DB name is not set. It should be defined in MongoDB URI or via setter");
		}
		if (StringUtils.isEmpty(changeLogsScanPackage)) {
			throw new MongobeeConfigurationException(
					"Scan package for changelogs is not set: use appropriate setter");
		}
	}

	/**
	 * Used DB name should be set here or via MongoDB URI (in a constructor)
	 *
	 * @param dbName
	 *            database name
	 * @return Mongobee object for fluent interface
	 */
	public Mongobee setDbName(String dbName) {
		this.dbName = dbName;
		return this;
	}

	/**
	 * Sets uri to MongoDB
	 *
	 * @param mongoClientURI
	 *            object with defined mongo uri
	 * @return Mongobee object for fluent interface
	 */
	public Mongobee setMongoClientURI(MongoClientURI mongoClientURI) {
		this.mongoClientURI = mongoClientURI;
		return this;
	}

	/**
	 * Package name where @ChangeLog-annotated classes are kept.
	 *
	 * @param changeLogsScanPackage
	 *            package where your changelogs are
	 * @return Mongobee object for fluent interface
	 */
	public Mongobee setChangeLogsScanPackage(String changeLogsScanPackage) {
		this.changeLogsScanPackage = changeLogsScanPackage;
		return this;
	}

	/**
	 * @return true if Mongobee runner is enabled and able to run, otherwise
	 *         false
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Feature which enables/disables Mongobee runner execution
	 *
	 * @param enabled
	 *            MOngobee will run only if this option is set to true
	 * @return Mongobee object for fluent interface
	 */
	public Mongobee setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	public void setBundleContext(BundleContext bundleContext) {
		this.bundleContext = bundleContext;
	}
	
}
