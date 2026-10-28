package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Portable, versioned text representation of an authored model's bind rig.
 *
 * <p>It deliberately contains no renderer instances or Minecraft classes: a
 * regression fixture captured on one loader can be replayed on the other. Text
 * fields use backslash escaping; coordinates are preserved as round-trip floats.
 * This is a development fixture format, not a network protocol.</p>
 */
public final class SourceRigFixtureCodec {
    private static final String HEADER = "# EFPI source rig fixture v1";
    private static final int MAX_NODES = 4096;
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    public static String encode(SourceRigSnapshot snapshot) {
        if (snapshot.nodes().size() > MAX_NODES) {
            throw new IllegalArgumentException("Too many source rig nodes for a fixture");
        }
        StringBuilder result = new StringBuilder(HEADER).append('\n');
        result.append("rig\t").append(escape(snapshot.key().entityTypeId()))
                .append('\t').append(escape(snapshot.key().modelSignature()))
                .append('\t').append(snapshot.hadNonUniformBindScale() ? "1" : "0").append('\n');
        appendBounds(result.append("bounds"), snapshot.bounds());
        result.append('\n');

        snapshot.nodes().values().stream().sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(node -> {
                    SkeletonVec3 pos = node.bindModelTransform().position();
                    SkeletonQuat rot = node.bindModelTransform().rotation();
                    result.append("node\t").append(escape(node.id()))
                            .append('\t').append(escape(node.name()))
                            .append('\t').append(escape(node.parentId().orElse("")))
                            .append('\t').append(node.renderable() ? "1" : "0")
                            .append('\t').append(pos.x()).append('\t').append(pos.y()).append('\t').append(pos.z())
                            .append('\t').append(rot.x()).append('\t').append(rot.y())
                            .append('\t').append(rot.z()).append('\t').append(rot.w());
                    if (node.geometryBounds().isPresent()) {
                        appendBounds(result, node.geometryBounds().orElseThrow());
                    } else {
                        result.append("\t-\t-\t-\t-\t-\t-");
                    }
                    result.append('\n');
                });
        if (result.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Source rig fixture is too large");
        }
        return result.toString();
    }

    public static SourceRigSnapshot decode(String source) {
        if (source == null || source.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Missing or oversized source rig fixture");
        }
        String[] lines = source.split("\\R", -1);
        if (lines.length < 4 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unknown source rig fixture format");
        }
        String[] meta = fields(lines[1], "rig", 4);
        String[] global = fields(lines[2], "bounds", 7);
        SkeletonKey key = new SkeletonKey(unescape(meta[1]), unescape(meta[2]));
        boolean nonUniform = flag(meta[3]);
        BodyBounds bounds = parseBounds(global, 1);
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        for (int i = 3; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            if (nodes.size() >= MAX_NODES) {
                throw new IllegalArgumentException("Too many source rig nodes for a fixture");
            }
            String[] fields = fields(lines[i], "node", 18);
            String id = unescape(fields[1]);
            String name = unescape(fields[2]);
            String parent = unescape(fields[3]);
            boolean renderable = flag(fields[4]);
            RigidTransform transform = new RigidTransform(
                    vector(fields, 5),
                    new SkeletonQuat(number(fields[8]), number(fields[9]),
                            number(fields[10]), number(fields[11])));
            boolean hasGeometry = !fields[12].isEmpty() && !"-".equals(fields[12]);
            BodyBounds geometry = hasGeometry ? parseBounds(fields, 12) : null;
            if (geometry == null) {
                for (int j = 13; j < 18; j++) {
                    if (!fields[j].equals(fields[12])) {
                        throw new IllegalArgumentException("Incomplete optional geometry for " + id);
                    }
                }
            }
            SourceNodeSnapshot previous = nodes.putIfAbsent(id, new SourceNodeSnapshot(
                    id, name, parent.isEmpty() ? null : parent, transform, geometry, renderable));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate source rig node: " + id);
            }
        }
        return new SourceRigSnapshot(key, nodes, bounds, nonUniform);
    }

    private static void appendBounds(StringBuilder out, BodyBounds bounds) {
        out.append('\t').append(bounds.min().x()).append('\t').append(bounds.min().y())
                .append('\t').append(bounds.min().z()).append('\t').append(bounds.max().x())
                .append('\t').append(bounds.max().y()).append('\t').append(bounds.max().z());
    }

    private static BodyBounds parseBounds(String[] fields, int first) {
        return new BodyBounds(vector(fields, first), vector(fields, first + 3));
    }

    private static SkeletonVec3 vector(String[] fields, int first) {
        return new SkeletonVec3(number(fields[first]), number(fields[first + 1]), number(fields[first + 2]));
    }

    private static float number(String source) {
        float value = Float.parseFloat(source);
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Source rig fixture contains a non-finite number");
        }
        return value;
    }

    private static boolean flag(String source) {
        return switch (source) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalArgumentException("Invalid source rig fixture boolean: " + source);
        };
    }

    private static String[] fields(String line, String expected, int count) {
        String[] result = line.split("\t", -1);
        if (result.length != count || !expected.equals(result[0])) {
            throw new IllegalArgumentException("Invalid source rig fixture " + expected + " record");
        }
        return result;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            if (++i >= value.length()) {
                throw new IllegalArgumentException("Incomplete source rig fixture escape");
            }
            decoded.append(switch (value.charAt(i)) {
                case '\\' -> '\\';
                case 't' -> '\t';
                case 'n' -> '\n';
                case 'r' -> '\r';
                default -> throw new IllegalArgumentException("Unknown source rig fixture escape");
            });
        }
        return decoded.toString();
    }

    private SourceRigFixtureCodec() {
    }
}
