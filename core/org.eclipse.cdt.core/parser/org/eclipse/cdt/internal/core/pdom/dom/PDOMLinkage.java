/*******************************************************************************
 * Copyright (c) 2005, 2006 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 * IBM Corporation
 * Andrew Ferguson (Symbian)
 *******************************************************************************/

package org.eclipse.cdt.internal.core.pdom.dom;

import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.IName;
import org.eclipse.cdt.core.dom.IPDOMVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IQualifierType;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPNamespaceScope;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.index.IIndexLinkage;
import org.eclipse.cdt.internal.core.Util;
import org.eclipse.cdt.internal.core.dom.bid.CLocalBindingIdentityComparator;
import org.eclipse.cdt.internal.core.dom.bid.IBindingIdentityFactory;
import org.eclipse.cdt.internal.core.dom.parser.ASTInternal;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.db.BTree;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeComparator;
import org.eclipse.cdt.internal.core.pdom.db.IBTreeVisitor;
import org.eclipse.cdt.internal.core.pdom.db.IString;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Doug Schaefer
 * 
 * This class represents a collection of symbols that can be linked together at
 * link time. These are generally global symbols specific to a given language.
 */
public abstract class PDOMLinkage extends PDOMNamedNode implements IBindingIdentityFactory, IIndexLinkage {

	// record offsets
	private static final int ID_OFFSET   = PDOMNamedNode.RECORD_SIZE + 0;
	private static final int NEXT_OFFSET = PDOMNamedNode.RECORD_SIZE + 4;
	private static final int INDEX_OFFSET = PDOMNamedNode.RECORD_SIZE + 8;
	
	protected static final int RECORD_SIZE = PDOMNamedNode.RECORD_SIZE + 12;
	
	// node types
	protected static final int LINKAGE = 0; // special one for myself
	static final int POINTER_TYPE = 1;
	static final int QUALIFIER_TYPE = 2;
	
	protected static final int LAST_NODE_TYPE = QUALIFIER_TYPE;
	
	public PDOMLinkage(PDOM pdom, int record) {
		super(pdom, record);
	}

	protected PDOMLinkage(PDOM pdom, String languageId, char[] name) throws CoreException {
		super(pdom, null, name);
		Database db = pdom.getDB();

		// id
		db.putInt(record + ID_OFFSET, db.newString(languageId).getRecord());
		
		pdom.insertLinkage(this);
	}
	
	protected int getRecordSize() {
		return RECORD_SIZE;
	}

	public int getNodeType() {
		return LINKAGE;
	}

	public static IString getId(PDOM pdom, int record) throws CoreException {
		Database db = pdom.getDB();
		int namerec = db.getInt(record + ID_OFFSET);
		return db.getString(namerec);
	}
	
	public static int getNextLinkageRecord(PDOM pdom, int record) throws CoreException {
		return pdom.getDB().getInt(record + NEXT_OFFSET);
	}
		
	public void setNext(int nextrec) throws CoreException {
		pdom.getDB().putInt(record + NEXT_OFFSET, nextrec);
	}
	
	public BTree getIndex() throws CoreException {
		return new BTree(pdom.getDB(), record + INDEX_OFFSET, getIndexComparator());
	}
	
	public void accept(final IPDOMVisitor visitor) throws CoreException {
		super.accept(visitor);
		getIndex().accept(new IBTreeVisitor() {
			public int compare(int record) throws CoreException {
				return 0;
			}
			public boolean visit(int record) throws CoreException {
				PDOMBinding binding = pdom.getBinding(record);
				if (binding != null) {
					if (visitor.visit(binding))
						binding.accept(visitor);
					visitor.leave(binding);
				}
				return true;
			}
		});
	}
	
	public ILinkage getLinkage() throws CoreException {
		return this;
	}

	public final void addChild(PDOMNode child) throws CoreException {
		getIndex().insert(child.getRecord());
	}
	
	public PDOMNode getNode(int record) throws CoreException {
		switch (PDOMNode.getNodeType(pdom, record)) {
		case POINTER_TYPE:
			return new PDOMPointerType(pdom, record);
		case QUALIFIER_TYPE:
			return new PDOMQualifierType(pdom, record);
		}
		return null;
	}
	
	public PDOMNode addType(PDOMNode parent, IType type) throws CoreException {
		PDOMNode node;
		
		if (type instanceof IPointerType)
			node = new PDOMPointerType(pdom, parent, (IPointerType)type);
		else if (type instanceof IQualifierType)
			node = new PDOMQualifierType(pdom, parent, (IQualifierType)type);
		else
			node = null;
		
		if(node!=null) {
			parent.addChild(node);
		}
		
		return node;
	}

	public final IBTreeComparator getIndexComparator() {
		return new IBTreeComparator() {
			CLocalBindingIdentityComparator cmp = new CLocalBindingIdentityComparator(PDOMLinkage.this);
			public final int compare(int record1, int record2) throws CoreException {
				PDOMNode node1 = getNode(record1);
				PDOMNode node2 = getNode(record2);
				return cmp.compare((IBinding)node1,(IBinding)node2);
			}
		};
	}
	
	public abstract PDOMBinding addBinding(IASTName name) throws CoreException;
	
	public abstract PDOMBinding adaptBinding(IBinding binding) throws CoreException;
	
	public abstract PDOMBinding resolveBinding(IASTName name) throws CoreException;
	
	/**
	 * 
	 * @param binding
	 * @return <ul><li> null - skip this binding (don't add to pdom)
	 * <li>this - for filescope
	 * <li>a PDOMBinding instance - parent adapted binding
	 * </ul>
	 * @throws CoreException
	 */
	public PDOMNode getAdaptedParent(IBinding binding) throws CoreException {
		try {
		IScope scope = binding.getScope();
		if (scope == null) {
			return binding instanceof PDOMBinding ? this : null;
		}
		
		if (scope instanceof IIndexBinding) {
			IIndexBinding parent= ((IIndexBinding) scope).getParentBinding();
			if (parent == null) {
				return this;
			}
			return adaptBinding(parent);
		}
			
		// the scope is from the ast
		if (scope instanceof ICPPNamespaceScope) {
			IName name= scope.getScopeName();
			if (name != null && name.toCharArray().length == 0) {
				// skip unnamed namespaces
				return null;
			}
		}
		
		IASTNode scopeNode = ASTInternal.getPhysicalNodeOfScope(scope);
		if (scopeNode instanceof IASTCompoundStatement)
			return null;
		else if (scopeNode instanceof IASTTranslationUnit)
			return this;
		else {
			IName scopeName = scope.getScopeName();
			if (scopeName instanceof IASTName) {
				IBinding scopeBinding = ((IASTName) scopeName).resolveBinding();
				PDOMBinding scopePDOMBinding = adaptBinding(scopeBinding);
				if (scopePDOMBinding != null)
					return scopePDOMBinding;
			}
		}
		} catch (DOMException e) {
			throw new CoreException(Util.createStatus(e));
		}
		return null;
	}
	
	public abstract IBindingIdentityFactory getBindingIdentityFactory();
}
