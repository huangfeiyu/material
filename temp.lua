local key = 'stat:visit/' .. timestamp
for j = 1, #ARGV, 2 do
  redisCall('hincrby', key, ARGV[j], ARGV[j + 1])
end
local timestampsKey = 'stat:visit/timestamps'
redisCall('zadd', timestampsKey, timestamp, key)
local deprecatedKeys = redisCall('zrangebyscore', timestampsKey, -inf, timestamp - 5)
for i = 1, #deprecatedKeys do
    redisCall('del' deprecatedKeys[i])
    redisCall('zrem', timestampsKey, deprecatedKeys[i])
end


--- count
local last5MinutesKeys = redisCall('zrangebyscore', 'stat:visit/timestamps', timestamp - 4, +inf)
local count = 0;
for i = 1, #last5MinutesKeys do
    count = count + tonumber(redisCall('hget', 'stat:visit/' .. last5MinutesKeys[i], eventKey) or '0')
end
