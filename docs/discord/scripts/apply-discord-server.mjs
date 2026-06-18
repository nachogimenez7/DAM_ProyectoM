#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const discordDir = path.resolve(__dirname, "..");
const manifestPath = path.join(discordDir, "discord-server-manifest.json");
const copyPath = path.join(discordDir, "channel-copy.md");

const args = new Set(process.argv.slice(2));
const shouldApply = args.has("--apply");
const shouldPostMessages = args.has("--post-messages");

const token = process.env.DISCORD_BOT_TOKEN;
const guildId = process.env.DISCORD_GUILD_ID;
const apiBase = "https://discord.com/api/v10";

const permissionBits = {
  Administrator: 0x0000000000000008n,
  ViewChannel: 0x0000000000000400n,
  SendMessages: 0x0000000000000800n,
  ManageMessages: 0x0000000000002000n,
  KickMembers: 0x0000000000000002n,
  BanMembers: 0x0000000000000004n,
  ModerateMembers: 0x0000010000000000n,
  ReadMessageHistory: 0x0000000000010000n,
  AddReactions: 0x0000000000000040n,
  AttachFiles: 0x0000000000008000n,
  Connect: 0x0000000000100000n,
  Speak: 0x0000000000200000n
};

const channelTypes = {
  text: 0,
  voice: 2,
  category: 4,
  forum: 15
};

const manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
const channelCopy = await fs.readFile(copyPath, "utf8");

function usage() {
  console.log(`Usage:
  node docs/discord/scripts/apply-discord-server.mjs
  node docs/discord/scripts/apply-discord-server.mjs --apply
  node docs/discord/scripts/apply-discord-server.mjs --apply --post-messages

Required env for --apply:
  DISCORD_BOT_TOKEN=bot token from Discord Developer Portal
  DISCORD_GUILD_ID=server id for "Traidores | Oficial"

Default mode is dry-run and does not call Discord.`);
}

function hexToInt(hex) {
  return Number.parseInt(hex.replace("#", ""), 16);
}

function permissions(names = []) {
  return names.reduce((mask, name) => {
    const bit = permissionBits[name];
    if (bit === undefined) throw new Error(`Unknown permission: ${name}`);
    return mask | bit;
  }, 0n).toString();
}

function normalizeChannelName(name) {
  return name.toLowerCase();
}

function messageForHeading(heading) {
  const start = channelCopy.indexOf(heading);
  if (start === -1) throw new Error(`Heading not found in channel-copy.md: ${heading}`);

  const next = channelCopy.indexOf("\n## ", start + heading.length);
  const raw = channelCopy.slice(start + heading.length, next === -1 ? undefined : next).trim();
  return raw;
}

async function discord(method, route, body) {
  if (!shouldApply) {
    console.log(`[dry-run] ${method} ${route}`);
    if (body) console.log(JSON.stringify(body, null, 2));
    return null;
  }

  if (!token || !guildId) {
    usage();
    throw new Error("DISCORD_BOT_TOKEN and DISCORD_GUILD_ID are required when using --apply.");
  }

  const response = await fetch(`${apiBase}${route}`, {
    method,
    headers: {
      Authorization: `Bot ${token}`,
      "Content-Type": "application/json"
    },
    body: body ? JSON.stringify(body) : undefined
  });

  const text = await response.text();
  const parsed = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(`${method} ${route} failed: ${response.status} ${text}`);
  }

  return parsed;
}

async function getGuildState() {
  if (!shouldApply) return { roles: [], channels: [] };
  const [roles, channels] = await Promise.all([
    discord("GET", `/guilds/${guildId}/roles`),
    discord("GET", `/guilds/${guildId}/channels`)
  ]);
  return { roles, channels };
}

function roleByName(roles, name) {
  return roles.find((role) => role.name === name);
}

function channelByName(channels, name, type) {
  return channels.find((channel) => channel.name === normalizeChannelName(name) && channel.type === type);
}

function teamOverwrite(roleId) {
  return {
    id: roleId,
    type: 0,
    allow: permissions(["ViewChannel", "SendMessages", "ReadMessageHistory", "AddReactions", "AttachFiles", "Connect", "Speak"])
  };
}

function denyEveryone() {
  return {
    id: guildId ?? "GUILD_ID",
    type: 0,
    deny: permissions(["ViewChannel"])
  };
}

function allowMember(roleId) {
  return {
    id: roleId,
    type: 0,
    allow: permissions(["ViewChannel", "SendMessages", "ReadMessageHistory", "AddReactions", "AttachFiles", "Connect", "Speak"])
  };
}

function readOnlyMember(roleId) {
  return {
    id: roleId,
    type: 0,
    allow: permissions(["ViewChannel", "ReadMessageHistory", "AddReactions"]),
    deny: permissions(["SendMessages"])
  };
}

function overwritesForCategory(access, roleIds) {
  const overwrites = [];
  if (access !== "public-read") overwrites.push(denyEveryone());

  if (access === "public-read") {
    overwrites.push({
      id: guildId ?? "GUILD_ID",
      type: 0,
      allow: permissions(["ViewChannel", "ReadMessageHistory"]),
      deny: permissions(["SendMessages"])
    });
  }

  if (access === "members") overwrites.push(allowMember(roleIds.Miembro));
  if (access === "english") {
    overwrites.push(allowMember(roleIds.English));
  }

  for (const name of ["Fundador", "Administrador", "Moderador", "Equipo de desarrollo"]) {
    if (roleIds[name]) overwrites.push(teamOverwrite(roleIds[name]));
  }

  return overwrites;
}

function overwritesForChannel(channel, categoryAccess, roleIds) {
  if (!channel.locked) return undefined;

  const overwrites = [];
  if (categoryAccess === "public-read") {
    overwrites.push({
      id: guildId ?? "GUILD_ID",
      type: 0,
      allow: permissions(["ViewChannel", "ReadMessageHistory"]),
      deny: permissions(["SendMessages"])
    });
  } else {
    overwrites.push(readOnlyMember(roleIds.Miembro));
  }

  if (categoryAccess === "english") overwrites.push(readOnlyMember(roleIds.English));

  for (const name of ["Fundador", "Administrador", "Moderador", "Equipo de desarrollo"]) {
    if (roleIds[name]) overwrites.push(teamOverwrite(roleIds[name]));
  }

  return overwrites;
}

async function ensureRoles(existingRoles) {
  const roleIds = {};

  for (const role of manifest.roles) {
    const existing = roleByName(existingRoles, role.name);
    const payload = {
      name: role.name,
      color: hexToInt(role.color),
      hoist: role.hoist,
      mentionable: role.mentionable,
      permissions: permissions(role.permissions)
    };

    if (existing) {
      roleIds[role.name] = existing.id;
      console.log(`Role exists: ${role.name}`);
      continue;
    }

    const created = await discord("POST", `/guilds/${guildId}/roles`, payload);
    roleIds[role.name] = created?.id ?? `ROLE:${role.name}`;
    console.log(`Role created: ${role.name}`);
  }

  return roleIds;
}

async function ensureChannels(existingChannels, roleIds) {
  const channelIds = {};
  let position = 0;

  for (const category of manifest.categories) {
    const categoryPayload = {
      name: category.name,
      type: channelTypes.category,
      position: position++,
      permission_overwrites: overwritesForCategory(category.access, roleIds)
    };

    let categoryId;
    const existingCategory = channelByName(existingChannels, category.name, channelTypes.category);
    if (existingCategory) {
      categoryId = existingCategory.id;
      console.log(`Category exists: ${category.name}`);
    } else {
      const createdCategory = await discord("POST", `/guilds/${guildId}/channels`, categoryPayload);
      categoryId = createdCategory?.id ?? `CATEGORY:${category.name}`;
      console.log(`Category created: ${category.name}`);
    }

    for (const channel of category.channels) {
      const type = channelTypes[channel.type];
      const existing = channelByName(existingChannels, channel.name, type);
      if (existing) {
        channelIds[channel.name] = existing.id;
        console.log(`Channel exists: ${channel.name}`);
        continue;
      }

      const payload = {
        name: channel.name,
        type,
        parent_id: categoryId,
        topic: channel.topic,
        position: position++,
        default_auto_archive_duration: channel.autoArchiveDuration,
        permission_overwrites: overwritesForChannel(channel, category.access, roleIds)
      };

      const created = await discord("POST", `/guilds/${guildId}/channels`, payload);
      channelIds[channel.name] = created?.id ?? `CHANNEL:${channel.name}`;
      console.log(`Channel created: ${channel.name}`);
    }
  }

  return channelIds;
}

async function postSeedMessages(channelIds) {
  if (!shouldPostMessages) return;

  for (const item of manifest.messageSources) {
    const channelId = channelIds[item.channel];
    if (!channelId) {
      console.warn(`Skipping message for ${item.channel}: channel id not found.`);
      continue;
    }

    const content = messageForHeading(item.heading);
    if (!content) {
      console.warn(`Skipping message for ${item.channel}: empty content.`);
      continue;
    }

    await discord("POST", `/channels/${channelId}/messages`, { content });
    console.log(`Posted seed message: ${item.channel}`);
  }
}

console.log(`${shouldApply ? "Apply" : "Dry-run"} Traidores Discord manifest`);
if (!shouldApply) usage();

const state = await getGuildState();
const roleIds = await ensureRoles(state.roles);
const refreshedState = shouldApply ? await getGuildState() : state;
const channelIds = await ensureChannels(refreshedState.channels, roleIds);
await postSeedMessages(channelIds);

console.log("Done.");
