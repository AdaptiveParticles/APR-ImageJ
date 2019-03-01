/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package adaptiveparticles.viewer;

import bdv.viewer.ViewerPanel;
import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.InputActionBindings;

import javax.swing.*;

public class NavigationActions9 extends Actions
{
	public static final String TOGGLE_FUSED_MODE = "toggle fused mode";
	public static final String TOGGLE_GROUPING = "toggle grouping";
	public static final String SET_CURRENT_SOURCE = "set current source %d";
	public static final String TOGGLE_SOURCE_VISIBILITY = "toggle source visibility %d";
	public static final String NEXT_TIMEPOINT = "next timepoint";
	public static final String PREVIOUS_TIMEPOINT = "previous timepoint";

	/**
	 * Create navigation actions and install them in the specified
	 * {@link InputActionBindings}.
	 *
	 * @param inputActionBindings
	 *            {@link InputMap} and {@link ActionMap} are installed here.
	 * @param viewer
	 *            Navigation actions are targeted at this {@link ViewerPanel}.
	 * @param keyProperties
	 *            user-defined key-bindings.
	 */
	public static void installActionBindings(
			final InputActionBindings inputActionBindings,
			final AprVolumeRenderer viewer,
			final KeyStrokeAdder.Factory keyProperties )
	{
		final NavigationActions9 actions = new NavigationActions9( keyProperties );

		actions.modes( viewer );
		actions.sources( viewer );
		actions.time( viewer );

		actions.install( inputActionBindings, "navigation" );
	}

	public NavigationActions9( final KeyStrokeAdder.Factory keyConfig )
	{
		super( keyConfig, new String[] { "bdv", "navigation" } );
	}

	public void modes( final AprVolumeRenderer viewer )
	{
		runnableAction(
				() -> viewer.getVisibilityAndGrouping().setFusedEnabled( !viewer.getVisibilityAndGrouping().isFusedEnabled() ),
				TOGGLE_FUSED_MODE, "F" );
		runnableAction(
				() -> viewer.getVisibilityAndGrouping().setGroupingEnabled( !viewer.getVisibilityAndGrouping().isGroupingEnabled() ),
				TOGGLE_GROUPING, "G" );
	}

	public void time( final AprVolumeRenderer viewer )
	{
		runnableAction(
				() -> viewer.nextTimePoint(),
				NEXT_TIMEPOINT, "CLOSE_BRACKET", "M" );
		runnableAction(
				() -> viewer.previousTimePoint(),
				PREVIOUS_TIMEPOINT, "OPEN_BRACKET", "N" );
	}

	public void sources( final AprVolumeRenderer viewer )
	{
		final String[] numkeys = new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
		for ( int i = 0; i < numkeys.length; ++i )
		{
			final int sourceIndex = i;
			runnableAction(
					() -> viewer.getVisibilityAndGrouping().setCurrentGroupOrSource( sourceIndex ),
					String.format( SET_CURRENT_SOURCE, i ), numkeys[ i ] );
			runnableAction(
					() -> viewer.getVisibilityAndGrouping().toggleActiveGroupOrSource( sourceIndex ),
					String.format( TOGGLE_SOURCE_VISIBILITY, i ), "shift " + numkeys[ i ] );
		}
	}
}
