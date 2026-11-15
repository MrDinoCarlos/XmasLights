const mc = require('../../.tools/client-probe/node_modules/minecraft-protocol');
const fs = require('fs');
const version = process.argv[4] || '1.21.4';
const data = require('../../.tools/client-probe/node_modules/minecraft-data')(version);
const projections = new Set(); let metadataPackets = 0; let textDisplays = 0; let strobePackVerified = false;
const client = mc.createClient({ host: '127.0.0.1', port: Number(process.argv[2]), username: 'XLSourceProbe', auth: 'offline', version });
client.on('position', p => {
  client.write('teleport_confirm', { teleportId: p.teleportId });
  // The fixture owns movement. Teleports can be relative after translation;
  // echoing their raw coordinates as absolute movement can disconnect the probe.
});
client.on('chunk_batch_finished', () => client.write('chunk_batch_received', {chunksPerTick:16}));
client.on('spawn_entity', p => {
  if (p.type === data.entitiesByName.item_display.id) projections.add(p.entityId);
  if (p.type === data.entitiesByName.text_display.id) textDisplays++;
});
client.on('add_resource_pack', async p => {
  try {
    const response = await fetch(p.url); if (!response.ok) throw new Error('Pack HTTP '+response.status);
    const bytes = Buffer.from(await response.arrayBuffer());
    const hash = require('crypto').createHash('sha1').update(bytes).digest('hex');
    if (hash !== p.hash) throw new Error('StrobeLights pack SHA-1 mismatch');
    strobePackVerified = true;
    client.write('resource_pack_receive',{uuid:p.uuid,result:3});
    client.write('resource_pack_receive',{uuid:p.uuid,result:4});
    client.write('resource_pack_receive',{uuid:p.uuid,result:0});
  } catch(e) { console.error(e.message); }
});
client.on('entity_metadata', p => { if (projections.has(p.entityId)) metadataPackets++; });
client.on('error', e => console.error(e.message));
client.on('end', () => { fs.writeFileSync(process.argv[3], JSON.stringify({projectionSpawns:projections.size,metadataPackets,textDisplays,strobePackVerified},null,2)); process.exit(0); });
setTimeout(() => { client.end(); process.exit(1); }, 110000);
