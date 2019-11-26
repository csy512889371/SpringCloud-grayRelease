package com.start.commom.core;

import com.google.common.base.Optional;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneAvoidanceRule;
//import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.alibaba.nacos.NacosDiscoveryClient;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

public class GrayMetadataRule extends ZoneAvoidanceRule {
	public static final String META_DATA_KEY_VERSION = "version";

	private static final Logger logger = LoggerFactory.getLogger(GrayMetadataRule.class);

	@Override
	public Server choose(Object key) {

		List<Server> serverList = this.getPredicate().getEligibleServers(this.getLoadBalancer().getAllServers(), key);
		List<Server> servers = this.getLoadBalancer().getReachableServers();
//http://www.imooc.com/article/288660

		if (CollectionUtils.isEmpty(serverList)) {
			return null;
		}
		
		NacosDiscoveryClient NacosDiscoveryClient;

		String hystrixVer = CoreHeaderInterceptor.version.get();
		logger.info("======>GrayMetadataRule:  hystrixVer{}", hystrixVer);

		List<Server> noMetaServerList = new ArrayList<>();
		for (Server server : serverList) {
			Map<String, String> metadata = ((DiscoveryEnabledServer) server).getInstanceInfo().getMetadata();

			// version策略
			String metaVersion = metadata.get(META_DATA_KEY_VERSION);
			if (!StringUtils.isEmpty(metaVersion)) {
				if (metaVersion.equals(hystrixVer)) {
					return server;
				}
			} else {
				noMetaServerList.add(server);
			}
		}

		if (StringUtils.isEmpty(hystrixVer) && !noMetaServerList.isEmpty()) {
			logger.info("====> 无请求header...");
			return originChoose(noMetaServerList, key);
		}

		return null;
	}

	private Server originChoose(List<Server> noMetaServerList, Object key) {
		Optional<Server> server = getPredicate().chooseRoundRobinAfterFiltering(noMetaServerList, key);
		if (server.isPresent()) {
			return server.get();
		} else {
			return null;
		}
	}
}
