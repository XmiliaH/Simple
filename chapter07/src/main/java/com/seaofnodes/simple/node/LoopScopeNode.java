package com.seaofnodes.simple.node;

import java.util.HashSet;
import java.util.Set;

/**
 * Special handling for Scope used inside the loop body
 */
public class LoopScopeNode extends ScopeNode {

    /**
     * The scope of the loop before we entered the loop
     */
    private ScopeNode _head;
    /**
     * The loop region
     */
    private RegionNode _region;

    /**
     * Names we see referenced in the loop body that
     * we need to create phis for
     */
    private Set<String> _names = new HashSet<>();

    public LoopScopeNode(ScopeNode head, RegionNode region) {
        _head = head;
        _region = region;
    }

    private void addPhiIfNeeded(String name) {
        // _names lookup tells us if we see the name for the first time
        // We also need to check if the name existed at loop head or not
        // And if a local name was added in the body of the loop
        if (_names.contains(name)) return;
        Node head0 = _head.lookup_(name);
        if (head0 == null) return; // name not known at loop head
        Node body0 = super.lookup_(name);
        if (head0 != body0) return;      // binding changed in the body, presumably new local var
        Node body = super.lookup(name);  // May create phi
        Node head = _head.lookup(name);  // May create phi
        _names.add(name);
        assert body != null && head != null;
        // The phi's second value is not set here
        // We update this in finish() method below
        Node phi = new PhiNode(name, _region, head, null).peephole();
        _head.update(name, phi);
        super.update(name, phi);
    }

    @Override
    public Node update(String name, Node n) {
        addPhiIfNeeded(name);
        return super.update(name, n);
    }

    @Override
    public Node lookup(String name) {
        if (CTRL.equals(name))
            // CTRL is never a phi
            return super.lookup(name);
        addPhiIfNeeded(name);
        return super.lookup(name);
    }

    @Override
    public ScopeNode dup() {
        return dupTo(new LoopScopeNode(_head, _region));
    }

    @Override
    public ScopeNode dupTo(ScopeNode dup) {
        if (dup instanceof LoopScopeNode other) {
            other._names = new HashSet<>(_names);
        }
        return super.dupTo(dup);
    }

    @Override
    public Node mergeScopes(ScopeNode that) {
        if (that instanceof LoopScopeNode other) {
            _names.addAll(other._names);
        }
        return super.mergeScopes(that);
    }

    /**
     * Called once the loop body has been parsed
     */
    public void finish() {
        // For each name we created phi for, add the second input node
        // this is the value that was set in the body of the loop
        for (String name: _names) {
            PhiNode phi = (PhiNode) _head.lookup(name);
            phi.set_def(2, super.lookup(name));
        }
    }
}
