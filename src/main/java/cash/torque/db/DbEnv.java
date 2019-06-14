package cash.torque.db;

import java.io.File;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.fusesource.lmdbjni.ByteUnit;
import org.fusesource.lmdbjni.Constants;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cash.torque.config.DbProperties;

@Component
public class DbEnv implements CommandLineRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(DbEnv.class);
	
	@Autowired
	private DbProperties props;
	
	// env
	private Env lmdbEnv;
	private Env lmdbStatsEnv;
	
	// torque database handlers
	private Database dbBlockInfo;
	
	// stats db handlers
	private Database dbRewardByHeight;
	private Database dbAttacksInFuture;
	private Database dbAttacksInPast;
	private Database dbAttacksOnSize;
	
	@Override
	public void run(String... args) throws Exception {
		//LOGGER.info("-------- start DbEnv --------");
	}
	
	@PostConstruct
	public void initDbEnv() throws Exception {
		
		String torqueHomePath = props.getTorqueHome();
		String LMDB_DIRNAME = props.getBlockchain().getEnvPath();
		String LMDB_STATS_DIRNAME = props.getStats().getEnvPath();
		
		LOGGER.info("props={}",ToStringBuilder.reflectionToString(props, ToStringStyle.SHORT_PREFIX_STYLE));
		
		LOGGER.info("STELLITE_HOME_PATH={}", torqueHomePath);
		
		// find ~/.torque home dir
		LOGGER.info("RAMILA098={}", torqueHomePath);
		File torqueHome = new File(torqueHomePath);
		if(!torqueHome.exists() || !torqueHome.isDirectory() || !torqueHome.canRead() || !torqueHome.canWrite()) {
			throw new Exception(".torque directory does not exist or is not a directory");
		}
		
		// search lmdb & lmdb_stats dir
		File[] filteredFiles = torqueHome.listFiles((dir,name)-> name.startsWith(LMDB_DIRNAME));
		File lmdbDir = null;
		File lmdbStatsDir = null;
		//boolean createDbStats = false;
		if(filteredFiles!=null && filteredFiles.length>0) {
			for(File f : filteredFiles) {
				if(f.isDirectory() && f.canRead() && f.canWrite()) {
					if(f.getName().equals(LMDB_DIRNAME)) {
						LOGGER.info("found LMDB path : {}", f.getAbsolutePath());
						lmdbDir = f;
					} else if(f.getName().equals(LMDB_STATS_DIRNAME)){
						LOGGER.info("found LMDB Stats path : {}", f.getAbsolutePath());
						lmdbStatsDir = f;
					}
				}
			}
			
			// if lmdb_stats dir does not exit create it
			if(lmdbStatsDir == null) {
				LOGGER.info("lmdb stats not found => try to create it");
				lmdbStatsDir = new File(torqueHomePath + File.separator + LMDB_STATS_DIRNAME);
				if(lmdbStatsDir.mkdir()) {
					LOGGER.info("lmdb stats dir successfuly created");
					//createDbStats = true;
				}
			}
		} else {
			throw new Exception(String.format("impossible to find %s directory in user home", torqueHomePath + File.separator + LMDB_DIRNAME));
		}
		
		// open LMDB databases READ ONLY!!!
		try {
			lmdbEnv = new Env(); // do not use constructor Env(String path) => it will create with maxDbs=1 !!!
			String path = lmdbDir.getPath();
			int flags = lmdbEnv.getFlags();
			int mode = Constants.RDONLY | Constants.NOLOCK | Constants.NOTLS;
			LOGGER.info("try to open environment {}", path);
			lmdbEnv.open(path, flags, mode);
			// block_info table
			// flags = MDB_INTEGERKEY | MDB_CREATE | MDB_DUPSORT | MDB_DUPFIXED
			// cf. https://github.com/monero-project/monero/blob/master/src/blockchain_db/lmdb/db_lmdb.cpp L1198
			LOGGER.info("try to open database {}", props.getBlockchain().getBlockInfo());
			dbBlockInfo = lmdbEnv.openDatabase(props.getBlockchain().getBlockInfo());
			LOGGER.info("database {} in env {} is opened", props.getBlockchain().getBlockInfo(), path);
		} catch(Exception e) {
			LOGGER.error("impossible to open env {} : {}", LMDB_DIRNAME, e.getMessage());
		}
		try {
			String path = lmdbStatsDir.getPath();
			lmdbStatsEnv = new Env(); // do not use constructor Env(String path) => it will create with maxDbs=1 !!!
			lmdbStatsEnv.setMapSize(4, ByteUnit.GIBIBYTES);
			lmdbStatsEnv.setMaxDbs(10);
			lmdbStatsEnv.open(path, Constants.CREATE);
			
			// open reward_by_height
			LOGGER.info("try to open database " + props.getStats().getRewardByHeight());
			dbRewardByHeight = lmdbStatsEnv.openDatabase(props.getStats().getRewardByHeight(), Constants.CREATE);
			LOGGER.info("database {} is opened ", props.getStats().getRewardByHeight());

			// open attack_future
			LOGGER.info("try to open database {}", props.getStats().getAttacksInFuture());
			dbAttacksInFuture = lmdbStatsEnv.openDatabase(props.getStats().getAttacksInFuture(), Constants.CREATE | Constants.DUPSORT | Constants.DUPFIXED | Constants.INTEGERKEY);
			LOGGER.info("database {} is opened", props.getStats().getAttacksInFuture());
			
			// open attack_past
			LOGGER.info("try to open database {}", props.getStats().getAttacksInPast());
			dbAttacksInPast = lmdbStatsEnv.openDatabase(props.getStats().getAttacksInPast(), Constants.CREATE | Constants.DUPSORT | Constants.DUPFIXED | Constants.INTEGERKEY);
			LOGGER.info("database {}  is opened", props.getStats().getAttacksInPast());
			
			// open attack_size
			LOGGER.info("try to open database {}", props.getStats().getAttacksOnSize());
			dbAttacksOnSize = lmdbStatsEnv.openDatabase(props.getStats().getAttacksOnSize(), Constants.CREATE | Constants.DUPSORT | Constants.DUPFIXED | Constants.INTEGERKEY);
			LOGGER.info("database {}  is opened", props.getStats().getAttacksOnSize());
			
		} catch(Exception e) {
			LOGGER.error("impossible to open databases in env {} : {}", LMDB_STATS_DIRNAME, e.getMessage());
		}
	}

	public Env getLmdbEnv() {
		return lmdbEnv;
	}
	public void setLmdbEnv(Env lmdbEnv) {
		this.lmdbEnv = lmdbEnv;
	}
	public Env getLmdbStatsEnv() {
		return lmdbStatsEnv;
	}
	public void setLmdbStatsEnv(Env lmdbStatsEnv) {
		this.lmdbStatsEnv = lmdbStatsEnv;
	}
	public Database getDbBlockInfo() {
		return dbBlockInfo;
	}
	public void setDbBlockInfo(Database dbBlockInfo) {
		this.dbBlockInfo = dbBlockInfo;
	}
	public Database getDbRewardByHeight() {
		return dbRewardByHeight;
	}
	public void setDbRewardByHeight(Database dbRewardByHeight) {
		this.dbRewardByHeight = dbRewardByHeight;
	}
	public Database getDbAttacksInFuture() {
		return dbAttacksInFuture;
	}
	public void setDbAttacksInFuture(Database dbAttacksFuture) {
		this.dbAttacksInFuture = dbAttacksFuture;
	}
	public Database getDbAttacksInPast() {
		return dbAttacksInPast;
	}
	public void setDbAttacksInPast(Database dbAttacksPast) {
		this.dbAttacksInPast = dbAttacksPast;
	}
	public Database getDbAttacksOnSize() {
		return dbAttacksOnSize;
	}
	public void setDbAttacksOnSize(Database dbAttacksOnSize) {
		this.dbAttacksOnSize = dbAttacksOnSize;
	}
	
}
